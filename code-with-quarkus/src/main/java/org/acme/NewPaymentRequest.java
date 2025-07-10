package org.acme;

import java.math.BigDecimal;
import java.time.Instant;

public record NewPaymentRequest(String correlationId, BigDecimal amount, Instant createdAt) {

    public RemotePaymentRequest toNewPayment() {
        return new RemotePaymentRequest(correlationId, amount);
    }

    public Payment toPayment(RemotePaymentName remotePaymentName) {
        return new Payment(correlationId, remotePaymentName, amount, createdAt);
    }
}