package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.acme.domain.PaymentWorker;
import org.acme.domain.PaymentsSummary;
import org.eclipse.microprofile.health.HealthCheck;

@ApplicationScoped
@Path("/no-op")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DummyInternalPaymentsResources {

    private final PaymentWorker paymentWorker;

    @Inject
    public DummyInternalPaymentsResources(PaymentWorker paymentWorker) {
        this.paymentWorker = paymentWorker;
    }

    @GET
    @Path("/internal/payments-summary")
    public PaymentsSummary getSummary(
            @QueryParam("from") @DefaultValue("") String fromStr,
            @QueryParam("to") @DefaultValue("") String toStr) {
        return PaymentsSummary.ZERO;
    }

    @Path("/internal/purge-payments")
    @POST
    public void purgeInternalPayments() {
        // do nothing
    }

    @Path("/purge-queue")
    @DELETE
    public void purgeQueueWorker() {
        paymentWorker.purge();
    }

    @Path("/q/health/ready")
    @GET
    public String healthReadyCheck() {
        return "{}";
    }

}
