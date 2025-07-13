package org.acme.application;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.acme.domain.Payments;
import org.acme.domain.RemoteHealthService;
import org.acme.domain.RemotePaymentName;
import org.acme.domain.RemotePaymentProcessorHealth;
import org.acme.domain.RemotePaymentResponse;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.acme.domain.RemotePaymentName.DEFAULT;
import static org.acme.domain.RemotePaymentName.FALLBACK;

@ApplicationScoped
public class NewPaymentTaskExecutor {

    private final RemoteHealthService healthState;
    private final Payments payments;

    @Inject
    public NewPaymentTaskExecutor(
            RemoteHealthService healthState,
            Payments payments) {
        this.healthState = healthState;
        this.payments = payments;
    }

    public void execute(NewPaymentRequest newPaymentRequest) throws IllegalStateException {

        RemotePaymentProcessorHealth defaultHealth = DEFAULT.healthState(healthState);
        RemotePaymentProcessorHealth fallbackHealth = FALLBACK.healthState(healthState);

        RemotePaymentName remotePaymentName = DEFAULT;
        RemotePaymentProcessorHealth actualHealth = defaultHealth;
        if (defaultHealth.failing() && !fallbackHealth.failing()) {
            remotePaymentName = FALLBACK;
            actualHealth = fallbackHealth;
        }

        if (actualHealth.failing()) {
            throw new IllegalStateException(STR."All remote payment services are failing. Please try again later.");
        }

        var remotePaymentProcessorExecutor = remotePaymentExecutorOf(remotePaymentName, actualHealth);

        var newPayment = newPaymentRequest.toNewPayment();
        RestResponse<RemotePaymentResponse> response = null;
        try {
            System.out.println(STR."Sending \{newPayment} to \{remotePaymentName} remote payment service");
            response = remotePaymentProcessorExecutor.processPayment(newPayment);
        } catch (RuntimeException e) {
            healthState.notifyServiceUnavailable(DEFAULT);
        }
        switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                payments.register(remotePaymentName.toPayment(newPayment));
            }
            case SERVER_ERROR -> {
                System.out.println(STR."\{remotePaymentName} : \{response.getStatus()} - \{response.getEntity().message()}");
                if (DEFAULT.equals(remotePaymentName)) {
                    healthState.notifyServiceUnavailable(DEFAULT);
                }
                throw new IllegalStateException(
                        STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service. It'll be re-submitted...");
            }
            default -> {
                System.out.println(STR."\{remotePaymentName} : \{response.getStatus()} - \{response.getEntity().message()}");
                if (DEFAULT.equals(remotePaymentName)) {
                    healthState.notifyServiceUnavailable(DEFAULT);
                }
            }
        }
    }

    private RemotePaymentProcessorExecutor remotePaymentExecutorOf(RemotePaymentName remotePaymentName, RemotePaymentProcessorHealth actualHealth) {
        URI uri = URI.create(ConfigProvider.getConfig()
                .getValue("%s-payment-processor.url".formatted(remotePaymentName.value()), String.class));
        long timeout = 1500;
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(uri)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build(RemotePaymentProcessorExecutor.class);
    }

}