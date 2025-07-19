package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.domain.Payments;
import org.acme.infrastructure.Iso8601InstantConverter;

import java.time.Instant;

@Path("/payments-summary")
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsSummaryResource {

    private final Payments payments;

    @Inject
    public PaymentsSummaryResource(Payments payments) {
        this.payments = payments;
    }

    @GET
    public Response get(@QueryParam("from") @DefaultValue("") String fromStr,
                        @QueryParam("to") @DefaultValue("") String toStr) {

        if ("".equals(fromStr) || "".equals(toStr)) {
            return Response.ok(payments.getSummary(null, null)).build();
        }

        Instant from = Iso8601InstantConverter.parse(fromStr);
        Instant to = Iso8601InstantConverter.parse(toStr);

        if (from == null || to == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("from and to cannot be null.").build();
        }

        return Response.ok(payments.getSummary(from, to)).build();
    }

}
