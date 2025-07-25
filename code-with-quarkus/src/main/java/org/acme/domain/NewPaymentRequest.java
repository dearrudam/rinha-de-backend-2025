package org.acme.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record NewPaymentRequest(String correlationId, BigDecimal amount) {

    public RemotePaymentRequest toNewPayment() {
        return new RemotePaymentRequest(correlationId, amount, Instant.now(), 0, Duration.ZERO);
    }

}