package org.acme;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
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

    public Payments(RedisDataSource ds) {
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

        Predicate<Payment> fromWasOmitted = payment -> from == null;
        Predicate<Payment> toWasOmitted = payment -> to == null;

        Predicate<Payment> fromOrAfter = payment -> payment.createAt().isAfter(from);
        Predicate<Payment> toOrBefore = payment -> payment.createAt().isBefore(to);

        Predicate<Payment> fromTo = fromWasOmitted.or(fromOrAfter)
                .and(toWasOmitted.or(toOrBefore));

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
        of(paymentHashCommands.hkeys(HASH))
                .filter(Predicate.not(Collection::isEmpty))
                .ifPresent(keys -> paymentHashCommands.hdel(HASH, keys.toArray(new String[0])));
    }
}
