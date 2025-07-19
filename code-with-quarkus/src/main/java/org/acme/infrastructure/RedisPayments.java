package org.acme.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@ApplicationScoped
public class RedisPayments implements Payments {

    private final static String HASH = "payments";
    private final RedisExecutor redisExecutor;

    public RedisPayments(RedisExecutor redisExecutor) {
        this.redisExecutor = redisExecutor;
    }

    public static void register(RedisExecutor.RedisContext ctx, Payment newPayment) {
        ctx.jedis().hsetnx(HASH, newPayment.correlationId(), ctx.encodeToJSON(newPayment));
    }

    public PaymentsSummary getSummary(final Instant from, final Instant to) {
        return redisExecutor.retrieve(ctx -> getSummary(ctx, from, to));
    }

    public static PaymentsSummary getSummary(final RedisExecutor.RedisContext ctx, Instant from, Instant to) {
        Map<RemotePaymentName, PaymentSummary> summary = new HashMap<>();

        Predicate<Payment> fromTo = getPaymentPredicate(from, to);

        ctx.jedis()
                .hgetAll(HASH)
                .values()
                .stream()
                .map(json -> ctx.decodeFromJSON(json, Payment.class))
                .filter(fromTo)
                .forEach(payment -> {
                    summary.put(payment.processedBy(), summary.computeIfAbsent(
                                    payment.processedBy(), k -> PaymentSummary.ZERO)
                            .increment(payment));
                });

        return PaymentsSummary.of(summary);
    }

    private static Predicate<Payment> getPaymentPredicate(Instant from, Instant to) {
        Predicate<Payment> fromWasOmitted = unused -> from == null;
        Predicate<Payment> toWasOmitted = unused -> to == null;

        Predicate<Payment> afterOrEqualFrom = payment -> from != null && from.isBefore(payment.createAt()) || from.equals(payment.createAt());
        Predicate<Payment> beforeOrEqualTo = payment -> to != null && to.isAfter(payment.createAt()) || to.equals(payment.createAt());

        Predicate<Payment> fromTo = fromWasOmitted.or(afterOrEqualFrom)
                .and(toWasOmitted.or(beforeOrEqualTo));
        return fromTo;
    }

    public void purge() {
        redisExecutor.execute(RedisPayments::purge);
    }

    public static void purge(RedisExecutor.RedisContext ctx) {
        var jedis = ctx.jedis();
        jedis.keys(HASH + "*")
                .stream()
                .forEach(jedis::del);
    }
}
