package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record RemotePaymentRequest(String correlationId,
                                   @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                                   BigDecimal amount,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
                                   Instant requestedAt,
                                   long retryCount,
                                   @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                                   Duration retryDelay
                                   ) {

    public RemotePaymentRequest {
        requestedAt = Optional.ofNullable(requestedAt).orElse(Instant.now());
        retryCount = Optional.ofNullable(retryCount).orElse(0L);
        retryDelay = Optional.ofNullable(retryDelay).orElse(Duration.ZERO);
    }

    public RemotePaymentRequest retryOn(Duration duration){
        return new RemotePaymentRequest(correlationId, amount, requestedAt, retryCount + 1,
                Optional.ofNullable(retryDelay).map(d -> d.plus(duration)).orElse(duration)
        );
    }

}