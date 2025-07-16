package org.acme.infrastructure;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.NewPaymentRequest;
import org.acme.domain.NewPaymentTask;
import org.acme.domain.RemotePaymentProcessorNotAvailableException;
import org.acme.domain.RemotePaymentRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import redis.clients.jedis.args.ListDirection;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

@Startup
@ApplicationScoped
public class PaymentProcessorServiceExecutor {

    private final static Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PaymentProcessorServiceExecutor.class);
    private final static String PAYMENTS_TO_PROCESS = "payments_to_process";
    private final static String PAYMENTS_PENDING = "payments_pending";
    private final String instanceId;
    private final ExecutorService executorService;
    private final RedisPayments redisPayments;
    private final RedisRemoteHealthService healthService;
    private final RedisExecutor redisExecutor;
    private final Function<RedisExecutor.RedisContext, NewPaymentTask.NewPaymentTaskBuilder> newPaymentTaskBuilder;

    @Inject
    public PaymentProcessorServiceExecutor(
            @ConfigProperty(name = "instance.id")
            Optional<String> instanceId,
            RedisPayments redisPayments,
            RedisRemoteHealthService healthService,
            RedisExecutor redisExecutor) {
        this.redisPayments = redisPayments;
        this.healthService = healthService;
        this.redisExecutor = redisExecutor;
        this.instanceId = instanceId.orElseGet(() -> UUID.randomUUID().toString());
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.newPaymentTaskBuilder = newPaymentTaskBuilder();
    }

    private Function<RedisExecutor.RedisContext, NewPaymentTask.NewPaymentTaskBuilder> newPaymentTaskBuilder() {
        return ctx -> NewPaymentTask
                .withHealthStateResolver(name -> healthService.getHealth(ctx, name))
                .withRemotePaymentProcessorExecutorResolver(NewPaymentTask::remotePaymentExecutorOf)
                .withPaymentStore(payment -> RedisPayments.register(ctx, payment));
    }

    @Startup
    public void start() {
        System.out.println(STR."Initialing the \{getClass().getSimpleName()}");
        //for (var worker = 0; worker < 2; worker++) {
        this.executorService.execute(() -> {
            while (true) {
                try {
                    processPayment();
                } catch (Exception e) {
                    // I don't care about it
                }
            }
        });
        //}
    }

    private void processPayment() {
        final var queueProcess = STR."\{PAYMENTS_PENDING}_\{instanceId}";
        redisExecutor
                .execute(ctx -> {
                    ofNullable(ctx.jedis()
                            .blmove(PAYMENTS_TO_PROCESS, queueProcess, ListDirection.LEFT, ListDirection.LEFT, Duration.ofSeconds(30).toMillis()))
                            .map(ctx.converterFor(RemotePaymentRequest.class))
                            .ifPresent(request -> {
                                try {
                                    NewPaymentTask task = newPaymentTaskBuilder.apply(ctx).build();
                                    task.execute(request);
                                } catch (Exception ex) {
                                    LOGGER.error(STR."Error while processing the payment \{request}. \{ex.getMessage()}", ex);
                                    if (ex instanceof RemotePaymentProcessorNotAvailableException) {
                                        ctx.jedis().rpush(PAYMENTS_TO_PROCESS, ctx.encodeToJSON(request));
                                    }
                                }
                            });
                });

    }

    @PreDestroy
    public void preDestroy() {
        this.executorService.shutdownNow();
    }

    public void fireAndForget(NewPaymentRequest newPaymentRequest) {
        executorService.execute(() -> {
            try {
                redisExecutor.execute(ctx -> ctx.jedis().lpush(PAYMENTS_TO_PROCESS, ctx.encodeToJSON(newPaymentRequest.toNewPayment())));
            } catch (Exception ex) {
                LOGGER.error(STR."Error while processing the payment \{newPaymentRequest}. \{ex.getMessage()}", ex);
            }
        });
    }

}
