package org.acme.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.Payments;
import org.acme.domain.RemotePaymentName;
import org.acme.health.PaymentProcessorHealthState;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApplicationScoped
public class PaymentProcessorServiceExecutor {

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
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void preDestroy() {
        this.executorService.shutdownNow();
    }

    public void fireAndForget(NewPaymentRequest newPaymentRequest) {
        fireAndForget(newPaymentRequest, new NewPaymentItemExecutor(healthState, payments));
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
