package org.acme;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PaymentSummary(Integer totalRequests,
                             @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                             BigDecimal totalAmount) {

    public static PaymentSummary ZERO = new PaymentSummary(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN));

    public PaymentSummary increment(Payment payment) {
        return new PaymentSummary(
                totalRequests + 1,
                totalAmount.add(payment.amount()).setScale(2, RoundingMode.HALF_DOWN)
        );
    }
}