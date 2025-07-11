package org.acme.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.PaymentProcessorHealthState;
import org.acme.application.NewPaymentItemExecutor;
import org.acme.application.PaymentProcessorServiceExecutor;
import org.acme.domain.Payments;
import org.acme.domain.RemotePaymentName;
import org.acme.domain.RemotePaymentRequest;

import java.util.Map;

@Path("/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    private final PaymentProcessorServiceExecutor processorServiceExecutor;

    private final Map<RemotePaymentName, PaymentProcessorHealthState> healthStates;

    private final Payments payments;

    @Inject
    public PaymentsResource(PaymentProcessorServiceExecutor processorServiceExecutor,
                            Map<RemotePaymentName, PaymentProcessorHealthState> healthStates,
                            Payments payments) {
        this.processorServiceExecutor = processorServiceExecutor;
        this.healthStates = healthStates;
        this.payments = payments;
    }

    @POST
    public Response process(RemotePaymentRequest remotePaymentRequest) {
        processorServiceExecutor.fireAndForget(
                remotePaymentRequest.toNewPayment(),
                new NewPaymentItemExecutor(healthStates, payments)
        );
        return Response.created(null).build();
    }

    @GET
    @Path("/{correlationId}")
    public Response get(@PathParam("correlationId") String correlationId) {
        return payments.getByCorrelationId(correlationId)
                .map(Response::ok)
                .orElse(Response.status(Response.Status.NOT_FOUND))
                .build();
    }

}

