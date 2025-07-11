package org.acme;

public record PaymentProcessorHealthState(boolean failing, int minResponseTime) {

    public static PaymentProcessorHealthState UNHEALTH = new PaymentProcessorHealthState(true, 0);

}