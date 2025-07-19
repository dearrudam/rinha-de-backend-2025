package org.acme.infrastructure;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.DefaultPaymentProcessor;
import org.acme.domain.FallbackPaymentProcessor;
import org.acme.domain.NewPaymentRequest;
import org.acme.domain.NewPaymentTask;
import org.acme.domain.RemotePaymentRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.LongStream;

import static java.util.Optional.ofNullable;

@Startup
@ApplicationScoped
public class PaymentProcessorService {

    private final static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PaymentProcessorService.class);
    private final static String PAYMENTS_TO_PROCESS = "payments_to_process";
    private final ExecutorService executorService;
    private final int batchSize;
    private final DefaultPaymentProcessor defaultPaymentProcessorService;
    private final FallbackPaymentProcessor fallbackPaymentProcessor;
    private final RedisExecutor redisExecutor;
    private final Function<RedisExecutor.RedisContext, NewPaymentTask.NewPaymentTaskBuilder> newPaymentTaskBuilder;
    private final BlockingQueue<NewPaymentRequest> queue = new LinkedBlockingDeque<>();
    private final String instanceId;
    private final Semaphore semaphore;

    @Inject
    public PaymentProcessorService(
            @ConfigProperty(name = "instance.id")
            Optional<String> instanceId,
            @ConfigProperty(name = "batch-size", defaultValue = "1000")
            int batchSize,
            @RestClient
            DefaultPaymentProcessor defaultPaymentProcessorService,
            @RestClient
            FallbackPaymentProcessor fallbackPaymentProcessor,
            RedisExecutor redisExecutor) {
        this.batchSize = batchSize;
        this.defaultPaymentProcessorService = defaultPaymentProcessorService;
        this.fallbackPaymentProcessor = fallbackPaymentProcessor;
        this.redisExecutor = redisExecutor;
        this.instanceId = instanceId.orElseGet(() -> UUID.randomUUID().toString());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.newPaymentTaskBuilder = newPaymentTaskBuilder();
        int availableProcessorForPaymentProcessing = (int) Math.round(Runtime.getRuntime().availableProcessors() * 0.5);
        this.semaphore = new Semaphore(availableProcessorForPaymentProcessing);
    }

    private Function<RedisExecutor.RedisContext, NewPaymentTask.NewPaymentTaskBuilder> newPaymentTaskBuilder() {
        return ctx -> NewPaymentTask
                .withRemotePaymentProcessorExecutorResolver((name) -> switch (name) {
                    case DEFAULT -> defaultPaymentProcessorService;
                    default -> fallbackPaymentProcessor;
                }).withPaymentStore(payment -> RedisPayments.register(ctx, payment));
    }

    @Startup
    public void start() {
        System.out.println("Initialing the " + getClass().getSimpleName());
        long workers = Math.round(Math.round(Runtime.getRuntime().availableProcessors() * 0.5) * 0.5);
        System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Workers: " + workers);
        System.out.println("Batch size: " + batchSize);

        LongStream.range(0, workers)
                .forEach(worker -> {
                    this.executorService.execute(() -> {
                        System.out.println("Starting worker " + worker + "...");
                        while (true) {
                            try {
                                semaphore.acquire();
                                processPayment();
                            } catch (Exception e) {
                                // I don't care about it
                            } finally {
                                semaphore.release();
                            }
                        }
                    });
                });

        this.executorService.execute(() -> {
            System.out.println("Starting payment to process collector ...");
            while (true) {
                try {
                    var pendingRequests = new ArrayList<NewPaymentRequest>();
                    if (queue.drainTo(pendingRequests, batchSize) > 0) {
                        redisExecutor.execute(ctx ->
                                ctx.jedis()
                                        .lpush(PAYMENTS_TO_PROCESS + ":" + instanceId,
                                                pendingRequests.stream()
                                                        .map(ctx::encodeToJSON)
                                                        .toArray(String[]::new))
                        );
                    }
                } catch (Exception e) {
                    // I don't care about it
                }
            }
        });
    }

    private void processPayment() {
        redisExecutor
                .execute(ctx -> {
                    ofNullable(ctx.jedis()
                            .lpop(PAYMENTS_TO_PROCESS + ":" + instanceId, batchSize))
                            .stream()
                            .flatMap(Collection::stream)
                            .map(ctx.converterFor(RemotePaymentRequest.class))
                            .parallel()
                            .forEach(request -> {
                                try {
                                    sleepIfNeeded(request);
                                    NewPaymentTask task = newPaymentTaskBuilder.apply(ctx).build();
                                    task.execute(request);
                                } catch (RuntimeException ex) {
                                    LOGGER.error("Error while processing the payment {}. {}", request, ex.getMessage());
                                }
                            });
                });

    }

    private void sleepIfNeeded(RemotePaymentRequest request) {
        ofNullable(request)
                .map(RemotePaymentRequest::retryDelay)
                .filter(Duration::isPositive)
                .ifPresent(delay -> {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @PreDestroy
    public void preDestroy() {
        this.executorService.shutdownNow();
    }

    public void fireAndForget(NewPaymentRequest newPaymentRequest) {
        queue.offer(newPaymentRequest);
    }

}
