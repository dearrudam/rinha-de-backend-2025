package org.acme.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public record PaymentsSummary(
        @JsonProperty("default") PaymentSummary defaultPaymentSummary,
        @JsonProperty("fallback") PaymentSummary fallbackPaymentSummary) {


    public static PaymentsSummary of(Map<RemotePaymentName, PaymentSummary> summary) {
        return of(summary.get(RemotePaymentName.DEFAULT), summary.get(RemotePaymentName.FALLBACK));
    }

    public static PaymentsSummary of(PaymentSummary defaultPaymentSummary, PaymentSummary fallbackPaymentSummary) {
        return new PaymentsSummary(defaultPaymentSummary, fallbackPaymentSummary);
    }

    public PaymentsSummary {
        defaultPaymentSummary = Optional.ofNullable(defaultPaymentSummary).orElse(PaymentSummary.ZERO);
        fallbackPaymentSummary = Optional.ofNullable(fallbackPaymentSummary).orElse(PaymentSummary.ZERO);
    }

}
