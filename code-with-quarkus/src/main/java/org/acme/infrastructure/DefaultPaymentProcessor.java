package org.acme.infrastructure;

import org.acme.health.RemotePaymentProcessorHealthCheck;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "default-payment-processor")
public interface DefaultPaymentProcessor extends RemotePaymentProcessorHealthCheck {
}
