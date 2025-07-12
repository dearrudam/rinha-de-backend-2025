package org.acme.domain;

import org.acme.health.PaymentProcessorHealthState;

import java.util.Map;

public enum RemotePaymentName {

    DEFAULT,
    FALLBACK;

    public String value() {
        return this.name().toLowerCase();
    }

    public PaymentProcessorHealthState healthState(Map<RemotePaymentName, PaymentProcessorHealthState> healthStates) {
        return healthStates.getOrDefault(this, PaymentProcessorHealthState.UNHEALTH);
    }

    public Payment toPayment(RemotePaymentRequest remotePaymentRequest) {
        return Payment.of(remotePaymentRequest.correlationId(), this,
                remotePaymentRequest.amount(),
                remotePaymentRequest.requestedAt());
    }
}
