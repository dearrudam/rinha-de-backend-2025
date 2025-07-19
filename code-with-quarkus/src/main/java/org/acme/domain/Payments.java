package org.acme.domain;

import java.time.Instant;


public interface Payments {

    PaymentsSummary getSummary(Instant from, Instant to);

    void purge();

}
