package org.acme.domain;

import io.vertx.core.impl.NoStackTraceTimeoutException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public class PaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessor.class);
    private final DefaultPaymentProcessor defaultPaymentProcessor;
    private final FallbackPaymentProcessor fallbackPaymentProcessor;

    @Inject
    public PaymentProcessor(
            @RestClient
            DefaultPaymentProcessor defaultPaymentProcessor,
            @RestClient
            FallbackPaymentProcessor fallbackPaymentProcessor) {
        this.defaultPaymentProcessor = defaultPaymentProcessor;
        this.fallbackPaymentProcessor = fallbackPaymentProcessor;
    }

    @Retry(maxRetries = 16)
    @Fallback(fallbackMethod = "fallbackSendPayment")
    public Optional<Payment> sendPayment(NewPaymentRequest newPaymentRequest) {
        RemotePaymentRequest request = newPaymentRequest.toNewPayment();
        defaultPaymentProcessor.processPayment(request);
        return Optional.of(RemotePaymentName.DEFAULT.toPayment(request));
    }

    public Optional<Payment> fallbackSendPayment(NewPaymentRequest newPaymentRequest) {
        final RemotePaymentRequest request = newPaymentRequest.toNewPayment();
        try {
            fallbackPaymentProcessor.processPayment(request);
            return Optional.of(RemotePaymentName.FALLBACK.toPayment(request));
        } catch (Exception ex) {
            Throwable throwable = getRootCause(ex);
            if (throwable instanceof NoStackTraceTimeoutException timeoutException) {
                LOGGER.warn("ProcessingException occurred while sending payment: {}", timeoutException.getMessage(), timeoutException);
                return Optional.of(RemotePaymentName.FALLBACK.toPayment(request));
            } else {
                LOGGER.error("Unexpected error occurred while sending payment: {}", throwable.getMessage(), throwable);
                return Optional.empty();
            }
        }
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        return (cause != null) && (throwable != cause) ? getRootCause(cause) : throwable;
    }
}