package org.acme;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        this.executorService.submit(() -> consumer.accept(newPaymentRequest));
    }

}
