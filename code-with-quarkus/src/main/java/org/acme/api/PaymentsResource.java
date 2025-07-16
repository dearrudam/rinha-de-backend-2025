package org.acme.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.domain.NewPaymentRequest;
import org.acme.infrastructure.PaymentProcessorServiceExecutor;

import java.math.BigDecimal;

@Path("/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    private final PaymentProcessorServiceExecutor processorServiceExecutor;

    @Inject
    public PaymentsResource(PaymentProcessorServiceExecutor processorServiceExecutor) {
        this.processorServiceExecutor = processorServiceExecutor;
    }

    record PaymentRequest(String correlationId,
                          @JsonFormat(shape = JsonFormat.Shape.NUMBER)
                          BigDecimal amount) {
        public NewPaymentRequest toNewPayment() {
            return new NewPaymentRequest(correlationId(), amount());
        }
    }

    @POST
    public Response process(PaymentRequest payment) {
        processorServiceExecutor.fireAndForget(payment.toNewPayment());
        return Response.created(null).build();
    }

}

