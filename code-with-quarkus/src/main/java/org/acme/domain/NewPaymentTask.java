package org.acme.domain;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.acme.domain.RemotePaymentName.DEFAULT;
import static org.acme.domain.RemotePaymentName.FALLBACK;

public record NewPaymentTask(Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorResolver,
                             Consumer<Payment> onSuccess) {

    public static WithRemotePaymentProcessorResolver withRemotePaymentProcessorExecutorResolver(
            Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {
        return new WithRemotePaymentProcessorResolver(remotePaymentProcessorExecutorResolver);
    }

    public record WithRemotePaymentProcessorResolver(
            Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {

        public WithRemotePaymentProcessorResolver {
            remotePaymentProcessorExecutorResolver =
                    Objects.requireNonNull(remotePaymentProcessorExecutorResolver, "remotePaymentProcessorExecutorResolver is required");
        }

        public WithRemotePaymentProcessorResolver withRemotePaymentProcessorExecutorResolver(
                Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {
            return new WithRemotePaymentProcessorResolver(remotePaymentProcessorExecutorResolver);
        }

        public NewPaymentTaskBuilder withPaymentStore(Consumer<Payment> paymentStore) {
            return new NewPaymentTaskBuilder(remotePaymentProcessorExecutorResolver, paymentStore);
        }

    }

    public record NewPaymentTaskBuilder(
            Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver,
            Consumer<Payment> paymentStore) {

        public NewPaymentTaskBuilder {
            remotePaymentProcessorExecutorResolver = Objects.requireNonNull(remotePaymentProcessorExecutorResolver, "remotePaymentProcessorExecutorResolver is required");
            paymentStore = Objects.requireNonNull(paymentStore, "paymentStore function is required");
        }

        public NewPaymentTaskBuilder withRemotePaymentProcessorExecutorResolver(
                Function<RemotePaymentName, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {
            return new NewPaymentTaskBuilder(remotePaymentProcessorExecutorResolver, paymentStore);
        }

        public NewPaymentTaskBuilder withPaymentStore(Consumer<Payment> paymentStore) {
            return new NewPaymentTaskBuilder(remotePaymentProcessorExecutorResolver, paymentStore);
        }

        public NewPaymentTask build() {
            return new NewPaymentTask(remotePaymentProcessorExecutorResolver, paymentStore);
        }
    }

    public Optional<Payment> execute(RemotePaymentRequest newPayment) throws RemotePaymentProcessorNotAvailableException, PaymentProcessException {

        RemotePaymentProcessorExecutor defaultPaymentProcessor = remotePaymentProcessorResolver.apply(DEFAULT);
        RemotePaymentProcessorExecutor fallbackPaymentProcessor = remotePaymentProcessorResolver.apply(FALLBACK);

        return submitPayment(newPayment,
                DEFAULT,
                defaultPaymentProcessor,
                fallbackRequest ->
                        submitPayment(fallbackRequest, FALLBACK, fallbackPaymentProcessor, rf -> Optional.empty()));
    }


    private Optional<Payment> submitPayment(RemotePaymentRequest newPayment,
                                            RemotePaymentName remotePaymentName,
                                            RemotePaymentProcessorExecutor paymentProcessor,
                                            Function<RemotePaymentRequest, Optional<Payment>> fallback) throws RemotePaymentProcessorNotAvailableException, PaymentProcessException {
        RestResponse<RemotePaymentResponse> response;
        try {
            response = paymentProcessor.processPayment(newPayment);
        } catch (Exception e) {
            return Optional.of(fallback.apply(newPayment)
                    .orElseThrow(() -> new RemotePaymentProcessorNotAvailableException(remotePaymentName,
                            "Unexpected error on submit to " + remotePaymentName.value() + " remote payment service. : " + e.getMessage())));
        }
        final var _response = response;
        switch (Response.Status.fromStatusCode(_response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                Payment payment = remotePaymentName.toPayment(newPayment);
                onSuccess.accept(payment);
                return Optional.of(payment);
            }
            case SERVER_ERROR -> {
                return Optional.of(fallback.apply(newPayment)
                        .orElseThrow(() -> new RemotePaymentProcessorNotAvailableException(remotePaymentName,
                                "Unexpected value: " + _response.getStatus() + " from " + remotePaymentName.value() + " remote payment service")));
            }
            default -> {
                throw new PaymentProcessException(remotePaymentName,
                        "Unexpected value: " + response.getStatus() + " from " + remotePaymentName.value() + " remote payment service.");
            }
        }
    }

}