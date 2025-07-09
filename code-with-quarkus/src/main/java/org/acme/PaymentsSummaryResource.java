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

    record Params( @QueryParam("from")
                   String rawFrom,
                   @QueryParam("to")
                   String rawTo){
    }

    @GET
    public PaymentsSummary get(Params params
           ) {
//        params.to().atOffset(ZoneOffset.UTC)
        return payments.getSummary(null, null);
    }

}
