package org.acme.application;

import org.acme.domain.RemotePaymentRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record NewPaymentRequest(String correlationId, BigDecimal amount) {

    public RemotePaymentRequest toNewPayment() {
        return new RemotePaymentRequest(correlationId, amount, Instant.now());
    }

}