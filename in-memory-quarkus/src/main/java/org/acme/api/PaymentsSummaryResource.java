package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.domain.PaymentService;
import org.acme.domain.PaymentsSummary;
import org.acme.infrastructure.InstantConverter;

import java.time.Instant;
import java.util.function.BiFunction;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class PaymentsSummaryResource {

    private final PaymentService paymentService;

    @Inject
    public PaymentsSummaryResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @POST
    @Path("/purge-payments")
    public void purge() {
        this.paymentService.purgePayments();
    }

    @POST
    @Path("/internal/purge-payments")
    public void internalPurge() {
        this.paymentService.purgeInternalPayments();
    }

    @GET
    @Path("/payments-summary")
    public Response get(@QueryParam("from") @DefaultValue("") String fromStr,
                        @QueryParam("to") @DefaultValue("") String toStr) {
        return getSummary(fromStr, toStr, paymentService::getSummary);
    }

    @Path("/internal/payments-summary")
    @GET
    public Response getInternalSummary(@QueryParam("from") @DefaultValue("") String fromStr,
                                       @QueryParam("to") @DefaultValue("") String toStr) {
        return getSummary(fromStr, toStr, paymentService::getInternalSummary);
    }

    private Response getSummary(String fromStr, String toStr, BiFunction<Instant, Instant, PaymentsSummary> summaryFunction) {

        if ("".equals(fromStr) || "".equals(toStr)) {
            return Response.ok(summaryFunction.apply(null,null)).build();
        }

        Instant from = InstantConverter.parse(fromStr);
        Instant to = InstantConverter.parse(toStr);

        if (from == null || to == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("from and to cannot be null.").build();
        }

        return Response.ok(summaryFunction.apply(from, to)).build();
    }

}
