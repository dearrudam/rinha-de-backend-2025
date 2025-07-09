package org.acme;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record RemotePaymentRequest(String correlationId,
                                   @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                                   BigDecimal amount) {

    NewPaymentRequest toNewPayment() {
        return new NewPaymentRequest(correlationId, amount, OffsetDateTime.now(ZoneOffset.UTC));
    }
}