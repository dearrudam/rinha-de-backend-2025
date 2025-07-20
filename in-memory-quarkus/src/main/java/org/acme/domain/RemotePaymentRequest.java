package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record RemotePaymentRequest(String correlationId,
                                   @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                                   BigDecimal amount,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                                   Instant requestedAt
) {

    public RemotePaymentRequest {
        requestedAt = Optional.ofNullable(requestedAt).orElse(Instant.now());
    }

}