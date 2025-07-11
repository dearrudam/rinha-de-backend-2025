package org.acme;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.acme.RemotePaymentName.DEFAULT;
import static org.acme.RemotePaymentName.FALLBACK;

public record NewPaymentItemExecutor(
        Map<RemotePaymentName, PaymentProcessorHealthState> healthState,
        Payments payments)
        implements Consumer<NewPaymentRequest> {

    @Override
    public void accept(NewPaymentRequest newPaymentRequest) {
        PaymentProcessorHealthState defaultHealth = DEFAULT.healthState(healthState);
        switch (defaultHealth) {
            case PaymentProcessorHealthState(var failing, _) when failing -> fallback(newPaymentRequest);
            default -> process(DEFAULT, defaultHealth, newPaymentRequest);
        }
    }

    private void fallback(NewPaymentRequest newPaymentRequest) {
        process(FALLBACK, FALLBACK.healthState(healthState), newPaymentRequest);
    }

    private void process(RemotePaymentName remotePaymentName,
                         PaymentProcessorHealthState defaultHealth,
                         NewPaymentRequest newPaymentRequest) {

        RemotePaymentProcessorExecutor remotePaymentProcessorExecutor = remotePaymentExecutorOf(remotePaymentName, defaultHealth);

        RemotePaymentRequest newPayment = newPaymentRequest.toNewPayment();
        RestResponse<RemotePaymentResponse> response = remotePaymentProcessorExecutor.processPayment(newPayment);

        switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                System.out.println(STR."Response Message: \"\{response.getEntity().message()}\" - Payment processed successfully by \{remotePaymentName.value()} remote payment service.");
                payments.register(newPayment.toPayment(remotePaymentName));
            }
            case SERVER_ERROR -> {
                if (DEFAULT.equals(remotePaymentName))
                    fallback(newPaymentRequest);
                else
                    throw new IllegalStateException(
                            STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service.");
            }
            default ->
                    throw new IllegalStateException(STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()}.");
        }
    }

    private RemotePaymentProcessorExecutor remotePaymentExecutorOf(
            RemotePaymentName remotePaymentName,
            PaymentProcessorHealthState defaultHealth) {
        URI uri = URI.create(ConfigProvider.getConfig()
                .getValue("%s-payment-processor.url".formatted(remotePaymentName.value()), String.class));
        long timeout = Integer.valueOf(defaultHealth.minResponseTime()).longValue();
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(uri)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .build(RemotePaymentProcessorExecutor.class);
    }

}