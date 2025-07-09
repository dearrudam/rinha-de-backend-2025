package org.acme;

import java.util.Map;

public enum RemotePaymentName {

    DEFAULT,
    FALLBACK;

    public String value() {
        return this.name().toLowerCase();
    }

    PaymentProcessorHealthState healthState(Map<RemotePaymentName, PaymentProcessorHealthState> healthStates) {
        return healthStates.getOrDefault(this, PaymentProcessorHealthState.UNHEALTH);
    }
}
