package org.acme.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class PaymentService {

    private final Payments payments;
    private final PaymentMiddleware paymentMiddleware;

    @Inject
    public PaymentService(Payments payments, PaymentMiddleware paymentMiddleware) {
        this.payments = payments;
        this.paymentMiddleware = paymentMiddleware;
    }

    public PaymentsSummary getSummary(Instant from, Instant to) {
        return getInternalSummary(from, to)
                .add(paymentMiddleware.getSummary(from, to));
    }

    public PaymentsSummary getInternalSummary(Instant from, Instant to) {
        return payments.getSummary(from, to);
    }

    public void purgePayments() {
        purgeInternalPayments();
        paymentMiddleware.purgePayments();
    }

    public void purgeInternalPayments() {
        payments.purge();
    }

}
