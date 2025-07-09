package org.acme;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
public class Payments {

    ReentrantLock lock = new ReentrantLock();
    Map<String, Payment> paymentsByCorrelationId = new HashMap<>();
    Map<String, PaymentSummary> summaryByPaymentService = new HashMap<>();

    @PostConstruct
    void postConstruct() {
        runSync(() -> {
            summaryByPaymentService.put("default", PaymentSummary.ZERO);
            summaryByPaymentService.put("fallback", PaymentSummary.ZERO);
        });
    }

    Payment register(Payment newPayment) {
        runSync(() -> {
            paymentsByCorrelationId.put(newPayment.correlationId(), newPayment);
            PaymentSummary newPaymentSummary = summaryByPaymentService.computeIfAbsent(
                            newPayment.paymentService(),
                            k -> new PaymentSummary(0, BigDecimal.ZERO.setScale(2)))
                    .increment(newPayment);
            summaryByPaymentService.put(newPayment.paymentService(), newPaymentSummary);
        });
        return newPayment;
    }

    private <T> T runSync(Callable<T> callable) {
        try {
            lock.lock();
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    private void runSync(Runnable runnable) {
        try {
            lock.lock();
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }


    Optional<Payment> getByCorrelationId(String correlationId) {
        return Optional.ofNullable(runSync(() -> paymentsByCorrelationId.get(correlationId)));
    }

    public PaymentsSummary getSummary(OffsetDateTime from, OffsetDateTime to) {

        Map<String, PaymentSummary> summary = runSync(() -> Collections.unmodifiableMap(summaryByPaymentService));

        return new PaymentsSummary(
                summary.getOrDefault("default", PaymentSummary.ZERO),
                summary.getOrDefault("fallback", PaymentSummary.ZERO)
        );
    }

    public void purge() {
        runSync(() -> {
            paymentsByCorrelationId.clear();
            summaryByPaymentService.put("default", PaymentSummary.ZERO);
            summaryByPaymentService.put("fallback", PaymentSummary.ZERO);
        });
    }
}
