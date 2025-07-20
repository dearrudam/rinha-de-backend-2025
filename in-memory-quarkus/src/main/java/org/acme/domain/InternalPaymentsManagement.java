package org.acme.domain;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "internal-payments-management")
public interface InternalPaymentsManagement {

    @Path("/internal/payments-summary")
    @GET
    PaymentsSummary getSummary(
            @QueryParam("from") @DefaultValue("") String fromStr,
            @QueryParam("to") @DefaultValue("") String toStr);

    @Path("/internal/purge-payments")
    @POST
    void purgeInternalPayments();

    @Path("/q/health/ready")
    @GET
    String healthReadyCheck();

}
