package org.acme.domain;

public class PaymentProcessException extends Exception {

        private final RemotePaymentName name;

        public PaymentProcessException(RemotePaymentName name, String message) {
            this(name, message, null);
        }

        public PaymentProcessException(RemotePaymentName name, String message, Throwable cause) {
            super(message, cause);
            this.name = name;
        }

        public RemotePaymentName name() {
            return name;
        }

    }