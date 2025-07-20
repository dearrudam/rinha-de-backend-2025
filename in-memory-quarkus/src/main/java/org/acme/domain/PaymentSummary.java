package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public record PaymentSummary(Integer totalRequests,
                             @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                             BigDecimal totalAmount) {
    public static PaymentSummary ZERO = new PaymentSummary(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));

    public static PaymentSummary of(Integer totalRequests, BigDecimal totalAmount) {
        return new PaymentSummary(totalRequests, totalAmount.setScale(2, RoundingMode.HALF_DOWN));
    }

    public PaymentSummary increment(Payment payment) {
        return new PaymentSummary(
                totalRequests + 1,
                totalAmount.add(payment.amount()).setScale(2, RoundingMode.HALF_DOWN)
        );
    }

    public PaymentSummary increment(Collection<Payment> payments) {
//        return payments
//                .stream()
//                .parallel()
//                .reduce(ZERO, PaymentSummary::increment, PaymentSummary::add);

        return PaymentSummary.of(payments.size(),
                payments.stream()
                        .parallel()
                        .map(Payment::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_DOWN));
    }

    public PaymentSummary add(PaymentSummary other) {
        return new PaymentSummary(
                this.totalRequests + other.totalRequests,
                this.totalAmount.add(other.totalAmount).setScale(2, RoundingMode.HALF_DOWN)
        );
    }
}