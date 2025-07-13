package org.acme.domain;

public interface RemoteHealthService {

    RemotePaymentProcessorHealth getHealth(RemotePaymentName name);

    void notifyServiceUnavailable(RemotePaymentName name);

}
