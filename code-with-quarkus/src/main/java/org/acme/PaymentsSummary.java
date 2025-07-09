package org.acme;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentsSummary(
        @JsonProperty("default") PaymentSummary defaultPaymentSummary,
        @JsonProperty("fallback") PaymentSummary fallbackPaymentSummary) {

    public static PaymentsSummary of(PaymentSummary defaultPaymentSummary, PaymentSummary fallbackPaymentSummary) {
        return new PaymentsSummary(defaultPaymentSummary, fallbackPaymentSummary);
    }

}
