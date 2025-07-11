package org.acme.application;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.core.Response;
import org.acme.PaymentProcessorHealthState;
import org.acme.RemotePaymentResponse;
import org.acme.domain.NewPaymentRequest;
import org.acme.domain.Payments;
import org.acme.domain.RemotePaymentName;
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

        var remotePaymentProcessorExecutor = remotePaymentExecutorOf(remotePaymentName,actualHealth);

        var newPayment = newPaymentRequest.toNewPayment();
        RestResponse<RemotePaymentResponse> response = remotePaymentProcessorExecutor.processPayment(newPayment);

        switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                System.out.println(STR."Response Message: \"\{response.getEntity().message()}\" - Payment processed successfully by \{remotePaymentName.value()} remote payment service.");
                payments.register(newPayment.toPayment(remotePaymentName));
            }
            case SERVER_ERROR -> {
                if (DEFAULT.equals(remotePaymentName))
                    healthState.put(DEFAULT, new PaymentProcessorHealthState(true, actualHealth.minResponseTime()));
                else
                    throw new IllegalStateException(
                            STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service. It'll be re-submitted...");
            }
            default ->
                    throw new IllegalStateException(STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()}.");
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