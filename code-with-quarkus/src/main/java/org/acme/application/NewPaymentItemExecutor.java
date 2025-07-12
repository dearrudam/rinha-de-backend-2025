package org.acme.application;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.core.Response;
import org.acme.domain.Payments;
import org.acme.domain.RemotePaymentName;
import org.acme.domain.RemotePaymentResponse;
import org.acme.health.PaymentProcessorHealthState;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.acme.domain.RemotePaymentName.DEFAULT;
import static org.acme.domain.RemotePaymentName.FALLBACK;

public record NewPaymentItemExecutor(
        Map<RemotePaymentName, PaymentProcessorHealthState> healthState,
        Payments payments)
        implements Consumer<NewPaymentRequest> {

    @Override
    public void accept(NewPaymentRequest newPaymentRequest) throws IllegalStateException {

        PaymentProcessorHealthState defaultHealth = DEFAULT.healthState(healthState);
        PaymentProcessorHealthState fallbackHealth = FALLBACK.healthState(healthState);

        RemotePaymentName remotePaymentName = DEFAULT;
        PaymentProcessorHealthState actualHealth = defaultHealth;
        if (defaultHealth.failing() || (defaultHealth.minResponseTime() > fallbackHealth.minResponseTime())) {
            remotePaymentName = FALLBACK;
            actualHealth = fallbackHealth;
        }

        if(actualHealth.failing()){
            throw new IllegalStateException(STR."All remote payment services are failing. Please try again later.");
        }

        var remotePaymentProcessorExecutor = remotePaymentExecutorOf(remotePaymentName, actualHealth);

        var newPayment = newPaymentRequest.toNewPayment();
        RestResponse<RemotePaymentResponse> response = null;
        try {
            response = remotePaymentProcessorExecutor.processPayment(newPayment);
        } catch (RuntimeException e) {
            healthState.put(DEFAULT, new PaymentProcessorHealthState(true, actualHealth.minResponseTime()));
        }
        switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                payments.register(remotePaymentName.toPayment(newPayment));
            }
            case SERVER_ERROR -> {
                System.out.println(STR."\{remotePaymentName} : \{response.getStatus()} - \{response.getEntity().message()}");
                if (DEFAULT.equals(remotePaymentName)) {
                    healthState.put(DEFAULT, new PaymentProcessorHealthState(true, actualHealth.minResponseTime()));
                }
                throw new IllegalStateException(
                        STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service. It'll be re-submitted...");
            }
            default -> {
                System.out.println(STR."\{remotePaymentName} : \{response.getStatus()} - \{response.getEntity().message()}");
                healthState.put(DEFAULT, new PaymentProcessorHealthState(true, actualHealth.minResponseTime()));
            }
        }
    }

    private RemotePaymentProcessorExecutor remotePaymentExecutorOf(RemotePaymentName remotePaymentName, PaymentProcessorHealthState actualHealth) {
        URI uri = URI.create(ConfigProvider.getConfig()
                .getValue("%s-payment-processor.url".formatted(remotePaymentName.value()), String.class));
        long timeout = actualHealth.minResponseTime();
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(uri)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build(RemotePaymentProcessorExecutor.class);
    }

}