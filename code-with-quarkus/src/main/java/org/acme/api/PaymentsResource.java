package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    @Inject
    PaymentProcessorServiceExecutor processorServiceExecutor;

    @Inject
    Map<RemotePaymentName, PaymentProcessorHealthState> healthStates;

    @Inject
    Payments payments;

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

