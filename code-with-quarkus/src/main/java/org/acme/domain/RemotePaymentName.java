package org.acme.domain;

import java.util.function.Function;

import static java.util.Optional.ofNullable;

public enum RemotePaymentName {

    DEFAULT,
    FALLBACK;

    public String value() {
        return this.name().toLowerCase();
    }

    public RemotePaymentProcessorHealth healthState(Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStates) {
        return ofNullable(healthStates.apply(this)).orElse(RemotePaymentProcessorHealth.UNHEALTH);
    }

    public Payment toPayment(RemotePaymentRequest remotePaymentRequest) {
        return Payment.of(remotePaymentRequest.correlationId(), this,
                remotePaymentRequest.amount(),
                remotePaymentRequest.requestedAt());
    }
}
