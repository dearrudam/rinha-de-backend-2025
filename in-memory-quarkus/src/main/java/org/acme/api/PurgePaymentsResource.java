package org.acme.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.acme.domain.PaymentService;

@Path("/")
@ApplicationScoped
@RunOnVirtualThread
public class PurgePaymentsResource {

    private final PaymentService paymentService;

    public PurgePaymentsResource(PaymentService paymentService) {
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
}
