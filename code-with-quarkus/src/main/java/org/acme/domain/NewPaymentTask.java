package org.acme.domain;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.acme.domain.RemotePaymentName.DEFAULT;
import static org.acme.domain.RemotePaymentName.FALLBACK;

public record NewPaymentTask(Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver,
                             BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorFunc,
                             Consumer<Payment> onSuccess) {

    public static WithHealthStateResolver withHealthStateResolver(
            Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver) {
        return new WithHealthStateResolver(healthStateResolver);
    }

    public record WithHealthStateResolver(
            Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver) {

        public WithHealthStateResolver {
            healthStateResolver = Objects.requireNonNull(healthStateResolver, "healthStateResolver function is required");
        }

        public WithHealthStateResolverAndRemotePaymentProcessorResolver withRemotePaymentProcessorExecutorResolver(
                BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {
            return new WithHealthStateResolverAndRemotePaymentProcessorResolver(healthStateResolver, remotePaymentProcessorExecutorResolver);
        }
    }

    public record WithHealthStateResolverAndRemotePaymentProcessorResolver(
            Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver,
            BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver) {

        public WithHealthStateResolverAndRemotePaymentProcessorResolver {
            healthStateResolver = Objects.requireNonNull(healthStateResolver, "healthStateResolver function is required");
            remotePaymentProcessorExecutorResolver = Objects.requireNonNull(remotePaymentProcessorExecutorResolver, "remotePaymentProcessorExecutorResolver is required");
        }

        public WithHealthStateResolverAndRemotePaymentProcessorResolver withHealthStateResolver(
                Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver) {
            return new WithHealthStateResolverAndRemotePaymentProcessorResolver(healthStateResolver, remotePaymentProcessorExecutorResolver);
        }

        public WithHealthStateResolverAndRemotePaymentProcessorResolver withRemotePaymentProcessorExecutorResolver(
                BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorFunc) {
            return new WithHealthStateResolverAndRemotePaymentProcessorResolver(healthStateResolver, remotePaymentProcessorExecutorFunc);
        }

        public NewPaymentTaskBuilder withPaymentStore(Consumer<Payment> paymentStore) {
            return new NewPaymentTaskBuilder(healthStateResolver, remotePaymentProcessorExecutorResolver, paymentStore);
        }

    }

    public record NewPaymentTaskBuilder(
            Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver,
            BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorResolver,
            Consumer<Payment> paymentStore) {

        public NewPaymentTaskBuilder {
            healthStateResolver = Objects.requireNonNull(healthStateResolver, "healthStateResolver function is required");
            remotePaymentProcessorExecutorResolver = Objects.requireNonNull(remotePaymentProcessorExecutorResolver, "remotePaymentProcessorExecutorResolver is required");
            paymentStore = Objects.requireNonNull(paymentStore, "paymentStore function is required");
        }

        public NewPaymentTaskBuilder withHealthStateResolver(Function<RemotePaymentName, RemotePaymentProcessorHealth> healthStateResolver) {
            return new NewPaymentTaskBuilder(healthStateResolver, remotePaymentProcessorExecutorResolver, paymentStore);
        }

        public NewPaymentTaskBuilder withRemotePaymentProcessorExecutorFunc(
                BiFunction<RemotePaymentName, RemotePaymentProcessorHealth, RemotePaymentProcessorExecutor> remotePaymentProcessorExecutorFunc) {
            return new NewPaymentTaskBuilder(healthStateResolver, remotePaymentProcessorExecutorFunc, paymentStore);
        }

        public NewPaymentTaskBuilder withPaymentStore(Consumer<Payment> paymentStore) {
            return new NewPaymentTaskBuilder(healthStateResolver, remotePaymentProcessorExecutorResolver, paymentStore);
        }

        public NewPaymentTask build() {
            return new NewPaymentTask(healthStateResolver, remotePaymentProcessorExecutorResolver, paymentStore);
        }
    }

    public Optional<Payment> execute(RemotePaymentRequest newPayment) throws IllegalStateException, RemotePaymentProcessorNotAvailableException, PaymentProcessException {

        RemotePaymentProcessorHealth defaultHealth = DEFAULT.healthState(healthStateResolver);
        RemotePaymentProcessorHealth fallbackHealth = FALLBACK.healthState(healthStateResolver);

        RemotePaymentName remotePaymentName = DEFAULT;
        RemotePaymentProcessorHealth actualHealth = defaultHealth;
        if (defaultHealth.failing() && !fallbackHealth.failing()) {
            remotePaymentName = FALLBACK;
            actualHealth = fallbackHealth;
        }

        if (actualHealth.failing()) {
            throw new RemotePaymentProcessorNotAvailableException(remotePaymentName, STR."All remote payment services are failing. Please try again later.");
        }

        var remotePaymentProcessorExecutor = remotePaymentProcessorExecutorFunc.apply(remotePaymentName, actualHealth);

        RestResponse<RemotePaymentResponse> response = null;
        try {
            System.out.println(STR."Sending \{newPayment} to \{remotePaymentName} remote payment service");
            response = remotePaymentProcessorExecutor.processPayment(newPayment);
        } catch (Exception e) {
            throw new RemotePaymentProcessorNotAvailableException(remotePaymentName,
                    STR."Unexpected error from \{remotePaymentName} remote payment service: \{e.getMessage()}. It'll be re-submitted...", e);
        }
        switch (Response.Status.fromStatusCode(response.getStatus()).getFamily()) {
            case SUCCESSFUL -> {
                Payment payment = remotePaymentName.toPayment(newPayment);
                onSuccess.accept(payment);
                return Optional.of(payment);
            }
            default -> {
                System.out.println(STR."\{remotePaymentName} : \{response.getStatus()} - \{response.getEntity().message()}");
                if (DEFAULT.equals(remotePaymentName)) {
                    throw new RemotePaymentProcessorNotAvailableException(remotePaymentName,
                            STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service. It'll be re-submitted...");
                }
                throw new PaymentProcessException(remotePaymentName,
                        STR."Unexpected value: \{response.getStatus()} from \{remotePaymentName.value()} remote payment service. It'll be re-submitted...");
            }
        }
    }

    public static RemotePaymentProcessorExecutor remotePaymentExecutorOf(RemotePaymentName remotePaymentName, RemotePaymentProcessorHealth actualHealth) {
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