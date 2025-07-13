package org.acme.domain;

import static java.util.Optional.ofNullable;

public enum RemotePaymentName {

    DEFAULT,
    FALLBACK;

    public String value() {
        return this.name().toLowerCase();
    }

    public RemotePaymentProcessorHealth healthState(RemoteHealthService healthStates) {
        return ofNullable(healthStates.getHealth(this)).orElse(RemotePaymentProcessorHealth.UNHEALTH);
    }

    public Payment toPayment(RemotePaymentRequest remotePaymentRequest) {
        return Payment.of(remotePaymentRequest.correlationId(), this,
                remotePaymentRequest.amount(),
                remotePaymentRequest.requestedAt());
    }
}
