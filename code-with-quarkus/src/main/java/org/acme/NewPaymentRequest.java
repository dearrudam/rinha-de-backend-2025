package org.acme;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NewPaymentRequest(String correlationId, BigDecimal amount, OffsetDateTime offsetDateTime) {

    public RemotePaymentRequest toNewPayment() {
        return new RemotePaymentRequest(correlationId, amount);
    }

    public Payment toPayment(RemotePaymentName remotePaymentName) {
        return new Payment(correlationId, remotePaymentName.value(), amount, offsetDateTime);
    }
}