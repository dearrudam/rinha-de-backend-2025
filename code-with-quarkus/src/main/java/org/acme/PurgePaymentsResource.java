package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/purge-payments")
public class PurgePaymentsResource {

    private final Payments payments;

    @Inject
    public PurgePaymentsResource(Payments payments) {
        this.payments = payments;
    }


    @POST
    public void purge() {
        payments.purge();
    }
}
