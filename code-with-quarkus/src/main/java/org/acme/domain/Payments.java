package org.acme.domain;

import java.time.Instant;
import java.util.Optional;


public interface Payments {

    public Payment register(Payment newPayment);

    public Optional<Payment> getByCorrelationId(String correlationId) ;

    public PaymentsSummary getSummary(Instant from, Instant to);

    public void purge();

}
