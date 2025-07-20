package org.acme.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSummaryTest {

    @Test
    void testZeroConstant() {
        PaymentSummary zero = PaymentSummary.ZERO;
        assertEquals(0, zero.totalRequests());
        assertEquals(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_DOWN), zero.totalAmount());
    }

    @Test
    void testIncrement() {
        Payment payment = Payment.of(
            "corr-1",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(10.50),
            Instant.now()
        );
        PaymentSummary summary = PaymentSummary.ZERO.increment(payment);
        assertEquals(1, summary.totalRequests());
        assertEquals(BigDecimal.valueOf(10.50).setScale(2, RoundingMode.HALF_DOWN), summary.totalAmount());
    }

    @Test
    void testIncrementMultipleTimes() {
        Payment payment1 = Payment.of(
            "corr-2",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(5.25),
            Instant.now()
        );
        Payment payment2 = Payment.of(
            "corr-3",
            RemotePaymentName.DEFAULT,
            BigDecimal.valueOf(4.75),
            Instant.now()
        );
        PaymentSummary summary = PaymentSummary.ZERO.increment(payment1).increment(payment2);
        assertEquals(2, summary.totalRequests());
        assertEquals(BigDecimal.valueOf(10.00).setScale(2, RoundingMode.HALF_DOWN), summary.totalAmount());
    }
}
