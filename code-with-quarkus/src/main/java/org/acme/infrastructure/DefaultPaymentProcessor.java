package org.acme;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "default-payment-processor")
public interface DefaultPaymentProcessor extends RemotePaymentProcessorHealthCheck {
}
