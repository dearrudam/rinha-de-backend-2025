package org.acme.infrastructure;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@ApplicationScoped
public class RedisPayments implements Payments {

    private final static String HASH = "payments";
    private final HashCommands<String, String, Payment> paymentHashCommands;
    private final KeyCommands<String> keyCommands;

    public RedisPayments(RedisDataSource ds) {
        this.keyCommands = ds.key();
        this.paymentHashCommands = ds.hash(Payment.class);
    }

    public Payment register(Payment newPayment) {
        paymentHashCommands.hsetnx(HASH, newPayment.correlationId(), newPayment);
        return newPayment;
    }

    public Optional<Payment> getByCorrelationId(String correlationId) {
        return Optional.ofNullable(paymentHashCommands.hget(HASH, correlationId));
    }

    public PaymentsSummary getSummary(Instant from, Instant to) {

        Map<RemotePaymentName, PaymentSummary> summary = new HashMap<>();

        Predicate<Payment> fromTo = getPaymentPredicate(from, to);

        Collection<Payment> payments = paymentHashCommands.hgetall(HASH)
                .values();
        payments
                .stream()
                .filter(fromTo)
                .forEach(payment -> {
                    summary.put(payment.processedBy(), summary.computeIfAbsent(
                                    payment.processedBy(), k -> PaymentSummary.ZERO)
                            .increment(payment));
                });

        return PaymentsSummary.of(summary);

    }

    private static Predicate<Payment> getPaymentPredicate(Instant from, Instant to) {
        Predicate<Payment> fromWasOmitted = _ -> from == null;
        Predicate<Payment> toWasOmitted = _ -> to == null;

        Predicate<Payment> afterOrEqualFrom = payment -> from != null && from.isBefore(payment.createAt()) || from.equals(payment.createAt());
        Predicate<Payment> beforeOrEqualTo = payment -> to != null && to.isAfter(payment.createAt()) || to.equals(payment.createAt());

        Predicate<Payment> fromTo = fromWasOmitted.or(afterOrEqualFrom)
                .and(toWasOmitted.or(beforeOrEqualTo));
        return fromTo;
    }

    public void purge() {
        keyCommands.del(HASH);
    }
}
