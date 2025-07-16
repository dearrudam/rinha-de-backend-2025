package org.acme.domain;

public class RemotePaymentProcessorNotAvailableException extends Exception {

    private final RemotePaymentName name;

    public RemotePaymentProcessorNotAvailableException(RemotePaymentName name, String message) {
        this(name, message, null);
    }

    public RemotePaymentProcessorNotAvailableException(RemotePaymentName name, String message, Throwable cause) {
        super(message, cause);
        this.name = name;
    }

    public RemotePaymentName name() {
        return name;
    }

}