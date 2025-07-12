package org.acme.health;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;


@Path("/payments")
public interface RemotePaymentProcessorHealthCheck {

    @GET
    @Path("/service-health")
    @Consumes(MediaType.APPLICATION_JSON)
    RestResponse<PaymentProcessorHealthState> healthCheck();
}
