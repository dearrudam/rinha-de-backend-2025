package org.acme.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Payment(String correlationId,
                      RemotePaymentName processedBy,
                      BigDecimal amount,
                      Instant createAt) {


    public static Payment of(String correlationId,
                             RemotePaymentName processedBy,
                             BigDecimal amount,
                             Instant createAt) {
        return new Payment(correlationId,
                processedBy,
                amount,
                createAt
        );
    }
}
