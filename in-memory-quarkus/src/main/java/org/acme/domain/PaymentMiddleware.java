package org.acme.domain;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Startup
    public void start() {
        Thread.startVirtualThread(()-> {
            while (true) {
                try {
                    String healthCheck = internalPaymentsManagement.healthReadyCheck();
                    LOGGER.info("Internal payments management service is ready: {}", healthCheck);
                    return;
                } catch (Exception e) {
                    LOGGER.warn("Error checking internal payments management service readiness: {}", e.getMessage(), e);
                    try {
                        Thread.sleep(500); // Retry after 1/2 second
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        LOGGER.error("Thread interrupted while waiting for internal payments management service readiness", ie);
                    }
                }
            }
        });
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
