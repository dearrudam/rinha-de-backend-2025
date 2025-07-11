package org.acme;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.Optional.of;

@ApplicationScoped
public class Payments {

    private final static String HASH = "payments";
    private final HashCommands<String, String, Payment> paymentHashCommands;
    private final KeyCommands<String> keyCommands;

    public Payments(RedisDataSource ds) {
        this.keyCommands = ds.key();
        this.paymentHashCommands = ds.hash(Payment.class);
    }

    public Payment register(Payment newPayment) {
        paymentHashCommands.hsetnx(HASH, newPayment.correlationId(), newPayment);
        return newPayment;
    }

    Optional<Payment> getByCorrelationId(String correlationId) {
        return Optional.ofNullable(paymentHashCommands.hget(HASH, correlationId));
    }

    public PaymentsSummary getSummary(Instant from, Instant to) {

        Map<RemotePaymentName, PaymentSummary> summary = new HashMap<>();

        Predicate<Payment> fromWasOmitted = _ -> from == null;
        Predicate<Payment> toWasOmitted = _ -> to == null;

        Predicate<Payment> afterOrEqualFrom = payment -> from != null && from.isBefore(payment.createAt()) || from.equals(payment.createAt());
        Predicate<Payment> beforeOrEqualTo = payment -> to != null && to.isAfter(payment.createAt()) || to.equals(payment.createAt());

        Predicate<Payment> fromTo = fromWasOmitted.or(afterOrEqualFrom)
                .and(toWasOmitted.or(beforeOrEqualTo));

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

    public void purge() {
        keyCommands.del(HASH);
    }
}
