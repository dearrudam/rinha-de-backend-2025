package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/payments-summary")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsSummaryResource {

    @Inject
    Payments payments;

    @GET
    public PaymentsSummary get(@QueryParam("from")
                               String from,
                               @QueryParam("to")
                               String to) {

        return payments.getSummary(Iso8601InstantConverter.parse(from), Iso8601InstantConverter.parse(to));
    }

}
