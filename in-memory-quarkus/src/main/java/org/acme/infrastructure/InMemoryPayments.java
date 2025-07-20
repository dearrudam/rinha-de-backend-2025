package org.acme.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.PaymentSummary;
import org.acme.domain.Payments;
import org.acme.domain.PaymentsSummary;
import org.acme.domain.RemotePaymentName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class InMemoryPayments implements Payments {

    private final ConcurrentLinkedQueue<Payment> payments = new ConcurrentLinkedQueue<>();

    @Override
    public PaymentsSummary getSummary(Predicate<Payment> filter) {
        return ofNullable(filter)
                .map(predicate -> {
                    final Map<RemotePaymentName, PaymentSummary> summaryMap = new HashMap<>();
                    Map<RemotePaymentName, List<Payment>> collected = currentPayments()
                            .stream()
                            .parallel()
                            .filter(predicate)
                            .collect(Collectors.groupingBy(p -> p.processedBy()));

                    collected.forEach((name, payments1) -> {
                        var summary = summaryMap.getOrDefault(name, PaymentSummary.ZERO).increment(payments1);
                        summaryMap.put(name, summary);
                    });
                    return PaymentsSummary.of(summaryMap);
                })
                .orElseGet(() -> PaymentsSummary.of(Map.of()));
    }

    private List<Payment> currentPayments() {
        return new ArrayList<>(payments);
    }

    @Override
    public void add(Payment payment) {
        this.payments.offer(payment);
    }

    @Override
    public void purge() {
        this.payments.clear();
    }
}
