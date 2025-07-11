package org.acme.application;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.NewPaymentRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@ApplicationScoped
public class PaymentProcessorServiceExecutor {

    private ExecutorService executorService;

    @PostConstruct
    public void postConstruct() {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void preDestroy() {
        this.executorService.shutdownNow();
    }

    public void fireAndForget(NewPaymentRequest newPaymentRequest, Consumer<NewPaymentRequest> consumer) {
        Runnable runnable = getRunnable(newPaymentRequest, consumer, this::fireAndForget);
        this.executorService.submit(runnable);
    }

    private Runnable getRunnable(NewPaymentRequest newPaymentRequest, Consumer<NewPaymentRequest> consumer, BiConsumer<NewPaymentRequest,Consumer<NewPaymentRequest>> onError) {
        return () -> {
            try {
                consumer.accept(newPaymentRequest);
            } catch (RuntimeException e) {
                onError.accept(newPaymentRequest, consumer);
            }
        };
    }

}
