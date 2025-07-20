package org.acme.infrastructure;

import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class RedisPayments implements Payments {

    private final static String HASH = "payments";
    private final RedisExecutor redisExecutor;

    public RedisPayments(RedisExecutor redisExecutor) {
        this.redisExecutor = redisExecutor;
    }

    @Override
    public PaymentsSummary getSummary(Predicate<Payment> filter) {
        return redisExecutor.retrieve(ctx -> getSummary(ctx, filter));
    }

    public static PaymentsSummary getSummary(final RedisExecutor.RedisContext ctx, Predicate<Payment> filter) {
        Map<RemotePaymentName, PaymentSummary> summary = new HashMap<>();
        ctx.jedis()
                .hgetAll(HASH)
                .values()
                .stream()
                .map(json -> ctx.decodeFromJSON(json, Payment.class))
                .filter(filter)
                .forEach(payment -> {
                    summary.put(payment.processedBy(), summary.computeIfAbsent(
                                    payment.processedBy(), k -> PaymentSummary.ZERO)
                            .increment(payment));
                });

        return PaymentsSummary.of(summary);
    }

    @Override
    public void add(Payment payment) {
        redisExecutor.execute(ctx -> add(ctx, payment));
    }

    public static void add(RedisExecutor.RedisContext ctx, Payment newPayment) {
        ctx.jedis().hsetnx(HASH, newPayment.correlationId(), ctx.encodeToJSON(newPayment));
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
