package org.acme;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Payment(String correlationId,
                      String paymentService,
                      BigDecimal amount,
                      OffsetDateTime dateTime) {

    public static Payment of(String correlationId,
                             String paymentService,
                             BigDecimal amount,
                             OffsetDateTime dateTime) {
        return new Payment(correlationId,
                paymentService,
                amount,
                dateTime
        );
    }
}
