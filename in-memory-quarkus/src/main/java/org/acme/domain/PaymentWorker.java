package org.acme.domain;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

@ApplicationScoped
public class PaymentWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentWorker.class);

    private final Payments payments;
    private final PaymentProcessor paymentProcessor;
    private final LinkedBlockingQueue<NewPaymentRequest> queue;
    private final int workers;

    @Inject
    public PaymentWorker(Payments payments,
                         PaymentProcessor paymentProcessor,
                         @ConfigProperty(name = "worker.queue-buffer", defaultValue = "10000")
                         int queueBuffer,
                         @ConfigProperty(name = "worker.size", defaultValue = "10")
                         int workers
    ) {
        this.payments = payments;
        this.paymentProcessor = paymentProcessor;
        this.queue = new LinkedBlockingQueue<>(queueBuffer);
        this.workers = workers;
    }

    @Startup
    public void start() {
        LOGGER.info("Starting worker threads with buffer size: {} and worker count: {}", queue.remainingCapacity(), workers);
        IntStream.range(0, workers).forEach(i ->
                Thread.startVirtualThread(this::consumeQueue)
        );
        LOGGER.info("Worker threads started successfully.");
    }

    private void consumeQueue() {
        while (true) {
            NewPaymentRequest paymentRequest = takeNewPaymentRequest();
            processPayment(paymentRequest);
        }
    }

    private NewPaymentRequest takeNewPaymentRequest() {
        try {
            return this.queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void processPayment(NewPaymentRequest paymentRequest) {
        paymentProcessor.sendPayment(paymentRequest)
                .ifPresentOrElse(payments::add, () -> this.accept(paymentRequest));
    }

    public boolean accept(NewPaymentRequest paymentRequest) {
        return this.queue.offer(paymentRequest);
    }

    public void purge() {
        this.queue.clear();
    }

}
