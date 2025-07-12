package org.acme.infrastructure;

import org.acme.health.RemotePaymentProcessorHealthCheck;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "fallback-payment-processor")
public interface FallbackPaymentProcessor extends RemotePaymentProcessorHealthCheck {
}
