package org.acme.application;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Optional.ofNullable;

@Startup
@ApplicationScoped
public class PaymentProcessorServiceExecutor {

    public static final int CAPACITY = 5000;

    private final BlockingDeque<NewPaymentRequest> queue = new LinkedBlockingDeque<>();
    private final NewPaymentTaskExecutor paymentTaskExecutor;
    private ExecutorService executorService;
    private final AtomicInteger bachSize = new AtomicInteger(CAPACITY);

    @Inject
    public PaymentProcessorServiceExecutor(
            NewPaymentTaskExecutor paymentTaskExecutor) {
        this.paymentTaskExecutor = paymentTaskExecutor;
    }

    public void setBachSize(Integer bachSize) {
        ofNullable(bachSize)
                .filter(size -> size > 0)
                .or(() -> Optional.of(CAPACITY))
                .ifPresent(this.bachSize::set);
    }

    public Integer batchSize() {
        return bachSize.get();
    }

    public Integer queueSize(){
        return this.queue.size();
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println(STR."Initialing the \{getClass().getSimpleName()}");
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        final Semaphore semaphore = new Semaphore(Runtime.getRuntime().availableProcessors());
        this.executorService.execute(() -> {
            while (true) {
                try {
                    final var batchSize = batchSize();
                    List<NewPaymentRequest> requests = new ArrayList<>(batchSize);
                    queue.drainTo(requests, batchSize);
                    if (requests.isEmpty()) {
                        continue;
                    }
                    semaphore.acquire();
                    executorService.execute(() -> {
                        List<NewPaymentRequest> rejectedRequests = new ArrayList<>(batchSize);
                        try {
                            requests.forEach(request -> {
                                try {
                                    paymentTaskExecutor.execute(request);
                                } catch (RuntimeException e) {
                                    rejectedRequests.add(request);
                                }
                            });
                        } finally {
                            queue.addAll(rejectedRequests);
                            semaphore.release();
                        }
                    });
                } catch (InterruptedException e) {
                    // I don't care about it
                }
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
