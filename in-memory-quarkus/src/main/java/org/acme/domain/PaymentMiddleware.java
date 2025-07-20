package org.acme.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class PaymentMiddleware {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentMiddleware.class);

    private final InternalPaymentsManagement internalPaymentsManagement;

    @Inject
    public PaymentMiddleware(
            @RestClient
            InternalPaymentsManagement internalPaymentsManagement) {
        this.internalPaymentsManagement = internalPaymentsManagement;
    }

    public void purgePayments() {
        try {
            internalPaymentsManagement.purgeInternalPayments();
        } catch (Exception e) {
            LOGGER.warn("Error purging internal payments: {} ", e.getMessage(), e);
        }
    }

    public PaymentsSummary getSummary(Instant from, Instant to) {
        try {
            return internalPaymentsManagement.getSummary(
                    ofNullable(from)
                            .map(Object::toString)
                            .orElse(""),
                    ofNullable(to)
                            .map(Object::toString)
                            .orElse(""));
        } catch (Exception e) {
            LOGGER.warn("Error fetching payment summary: {}", e.getMessage(), e);
            return PaymentsSummary.ZERO;
        }
    }
}
