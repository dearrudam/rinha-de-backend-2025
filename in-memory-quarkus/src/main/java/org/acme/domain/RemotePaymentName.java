package org.acme.domain;

public enum RemotePaymentName {

    DEFAULT,
    FALLBACK;

    public String value() {
        return this.name().toLowerCase();
    }

    public Payment toPayment(RemotePaymentRequest remotePaymentRequest) {
        return Payment.of(remotePaymentRequest.correlationId(), this,
                remotePaymentRequest.amount(),
                remotePaymentRequest.requestedAt());
    }
}
