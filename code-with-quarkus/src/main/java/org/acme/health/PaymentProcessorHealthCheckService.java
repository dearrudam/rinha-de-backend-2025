package org.acme.health;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.acme.domain.RemotePaymentName;
import org.acme.infrastructure.DefaultPaymentProcessor;
import org.acme.infrastructure.FallbackPaymentProcessor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class PaymentProcessorHealthCheckService {

    private final ConcurrentMap<RemotePaymentName, PaymentProcessorHealthState> state = new ConcurrentHashMap<>();
    private final DefaultPaymentProcessor defaultPaymentProcessorService;
    private final FallbackPaymentProcessor fallbackPaymentProcessorService;

    @Inject
    public PaymentProcessorHealthCheckService(
            @RestClient
            DefaultPaymentProcessor defaultPaymentProcessorService,
            @RestClient
            FallbackPaymentProcessor fallbackPaymentProcessorService) {
        this.defaultPaymentProcessorService = defaultPaymentProcessorService;
        this.fallbackPaymentProcessorService = fallbackPaymentProcessorService;
    }

    @Produces
    @ApplicationScoped
    public Map<RemotePaymentName, PaymentProcessorHealthState> healthState() {
        return state;
    }

    @Scheduled(every = "{default-payment-processor.healthcheck.interval}")
    public void checkHealthOfDefaultPaymentProcessorService() {
        setState(RemotePaymentName.DEFAULT, this.defaultPaymentProcessorService.healthCheck());
    }

    @Scheduled(every = "{fallback-payment-processor.healthcheck.interval}")
    public void checkHealthOfFallbackPaymentProcessorService() {
        setState(RemotePaymentName.FALLBACK, this.fallbackPaymentProcessorService.healthCheck());
    }

    private void setState(RemotePaymentName name, RestResponse<PaymentProcessorHealthState> response) {
        switch (response.getStatus()) {
            case 200 -> setStatus(name, response.getEntity());
            default -> setStatus(name, new PaymentProcessorHealthState(true, 0));
        }
    }

    private void setStatus(RemotePaymentName name, PaymentProcessorHealthState state) {
        this.state.put(name, state);
    }

}
