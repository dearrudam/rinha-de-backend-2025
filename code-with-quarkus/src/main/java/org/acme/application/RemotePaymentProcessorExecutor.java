package org.acme.application;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.domain.RemotePaymentRequest;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/payments")
public interface RemotePaymentProcessorExecutor {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    RestResponse<org.acme.RemotePaymentResponse> processPayment(RemotePaymentRequest request);

}
