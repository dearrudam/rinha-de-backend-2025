package org.acme.domain;

import java.time.Instant;
import java.util.function.Predicate;


public interface Payments {

    default PaymentsSummary getSummary(Instant from, Instant to) {
        return getSummary(paymentSummaryPredicate(from, to));
    }

    void add(Payment payment);

    PaymentsSummary getSummary(Predicate<Payment> predicate);

    void purge();

    static Predicate<Payment> paymentSummaryPredicate(Instant from, Instant to) {
        Predicate<Payment> fromWasOmitted = unused -> from == null;
        Predicate<Payment> toWasOmitted = unused -> to == null;

        Predicate<Payment> afterOrEqualFrom = payment -> from != null && from.isBefore(payment.createAt()) || from.equals(payment.createAt());
        Predicate<Payment> beforeOrEqualTo = payment -> to != null && to.isAfter(payment.createAt()) || to.equals(payment.createAt());

        Predicate<Payment> fromTo = fromWasOmitted.or(afterOrEqualFrom)
                .and(toWasOmitted.or(beforeOrEqualTo));
        return fromTo;
    }
}
