package org.acme.domain;

public record RemotePaymentProcessorHealth(boolean failing, int minResponseTime) {

    public static RemotePaymentProcessorHealth UNHEALTH = new RemotePaymentProcessorHealth(true, 0);
    public static RemotePaymentProcessorHealth HEALTH = new RemotePaymentProcessorHealth(false, 0);

}