package org.acme.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.Payments;
import org.acme.domain.RemotePaymentName;
import org.acme.health.PaymentProcessorHealthState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApplicationScoped
public class PaymentProcessorServiceExecutor {

    public static final int CAPACITY = 5000;
    private final BlockingDeque<NewPaymentRequest> queue = new LinkedBlockingDeque<>();
    private ExecutorService executorService;
    private final Map<RemotePaymentName, PaymentProcessorHealthState> healthState;
    private final Payments payments;

    @Inject
    public PaymentProcessorServiceExecutor(
            Map<RemotePaymentName, PaymentProcessorHealthState> healthState,
            Payments payments) {
        this.healthState = healthState;
        this.payments = payments;
    }

    @PostConstruct
    public void postConstruct() {
        final var newPaymentItemExecutor = new NewPaymentItemExecutor(healthState, payments);
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        final Semaphore semaphore = new Semaphore(Runtime.getRuntime().availableProcessors());
        this.executorService.execute(() -> {
            while (true) {
                try {
                    List<NewPaymentRequest> requests = new ArrayList<>(CAPACITY);
                    queue.drainTo(requests, CAPACITY);
                    if (requests.isEmpty()) {
                        continue;
                    }
                    semaphore.acquire();
                    Thread.startVirtualThread(() -> {
                        List<NewPaymentRequest> rejectedRequests = new ArrayList<>(CAPACITY);
                        try {
                            requests.forEach(request -> {
                                try {
                                    newPaymentItemExecutor.accept(request);
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
        //fireAndForget(newPaymentRequest, new NewPaymentItemExecutor(healthState, payments));
    }

    private void fireAndForget(NewPaymentRequest newPaymentRequest, Consumer<NewPaymentRequest> consumer) {
        this.executorService.submit(newTask(newPaymentRequest, consumer, this::fireAndForget));
    }

    private Runnable newTask(NewPaymentRequest newPaymentRequest, Consumer<NewPaymentRequest> consumer, BiConsumer<NewPaymentRequest, Consumer<NewPaymentRequest>> onError) {
        return () -> {
            try {
                consumer.accept(newPaymentRequest);
            } catch (RuntimeException e) {
                onError.accept(newPaymentRequest, consumer);
            }
        };
    }

}
