package org.acme.infrastructure;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.DefaultPaymentProcessor;
import org.acme.domain.FallbackPaymentProcessor;
import org.acme.domain.RemoteHealthService;
import org.acme.domain.RemotePaymentName;
import org.acme.domain.RemotePaymentProcessorHealth;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Startup
@ApplicationScoped
public class RedisRemoteHealthService implements RemoteHealthService {

    public static final String HEALTHCHECK = "healthcheck";
    private final String instanceId;
    private final RedisDataSource ds;
    private final Duration defaultHealthCheckInterval;
    private final DefaultPaymentProcessor defaultPaymentProcessorService;
    private final Duration fallbackHealthCheckInterval;
    private final FallbackPaymentProcessor fallbackPaymentProcessorService;
    private ExecutorService executorService;
    private final ValueCommands<String, RemotePaymentProcessorHealth> healthValues;
    private final ValueCommands<String, String> commands;

    @Inject
    public RedisRemoteHealthService(
            RedisDataSource ds,
            @ConfigProperty(name = "instance.id")
            Optional<String> instanceId,
            @ConfigProperty(name = "default-payment-processor.healthcheck.interval", defaultValue = "5s")
            Duration defaultHealthCheckInterval,
            @RestClient
            DefaultPaymentProcessor defaultPaymentProcessorService,
            @ConfigProperty(name = "fallback-payment-processor.healthcheck.interval", defaultValue = "5s")
            Duration fallbackHealthCheckInterval,
            @RestClient
            FallbackPaymentProcessor fallbackPaymentProcessorService) {
        this.ds = ds;
        this.commands = ds.value(String.class);
        this.healthValues = ds.value(RemotePaymentProcessorHealth.class);
        this.instanceId = instanceId
                .orElseGet(() -> UUID.randomUUID().toString());
        this.defaultHealthCheckInterval = defaultHealthCheckInterval;
        this.defaultPaymentProcessorService = defaultPaymentProcessorService;
        this.fallbackHealthCheckInterval = fallbackHealthCheckInterval;
        this.fallbackPaymentProcessorService = fallbackPaymentProcessorService;
    }

    @PreDestroy
    public void stop() {
        this.executorService.shutdownNow();
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println(STR."Initialing the \{getClass().getSimpleName()}");
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        boolean isLeader = commands.setnx("health-leader", this.instanceId);
        if (isLeader) {
            this.executorService.execute(updateStatusTask(RemotePaymentName.DEFAULT));
            this.executorService.execute(updateStatusTask(RemotePaymentName.FALLBACK));
        }
        System.out.println(STR."\{this.instanceId} is the leader ? \{isLeader}");
    }

    private Runnable updateStatusTask(RemotePaymentName name) {
        return () -> {
            while (true) {
                updateStatus(name);
            }
        };
    }

//    private void updateStatusIfIsLeader(RemotePaymentName name) {
//        try {
//            if (isLeader()) {
//                updateStatus(name);
//            }
//        } finally {
//            sleep(name);
//        }
//    }

    private void updateStatus(RemotePaymentName name) {
        try {
            RestResponse<RemotePaymentProcessorHealth> response = switch (name) {
                case DEFAULT -> defaultPaymentProcessorService.healthCheck();
                case FALLBACK -> fallbackPaymentProcessorService.healthCheck();
            };
            switch (response.getStatus()) {
                case 200 -> setStatus(name, response.getEntity());
                default -> setStatus(name, new RemotePaymentProcessorHealth(true, 0));
            }
        } catch (Exception ex) {
            setStatus(name, RemotePaymentProcessorHealth.UNHEALTH);
        }
    }

    private boolean isLeader() {
        return commands.setnx("health-leader", this.instanceId);
    }

    private void sleep(RemotePaymentName name) {
        try {
            switch (name) {
                case DEFAULT -> Thread.sleep(defaultHealthCheckInterval);
                case FALLBACK -> Thread.sleep(fallbackHealthCheckInterval);
            }
        } catch (InterruptedException ex) {
            // do nothing
        }
    }

    private void setStatus(RemotePaymentName name, RemotePaymentProcessorHealth state) {
        healthValues.set(STR."\{HEALTHCHECK}:\{name.name()}", state);
    }

    @Override
    public void notifyServiceUnavailable(RemotePaymentName name) {
        setStatus(name, RemotePaymentProcessorHealth.UNHEALTH);
    }

    @Override
    public RemotePaymentProcessorHealth getHealth(RemotePaymentName name) {
        return healthValues.get(STR."\{HEALTHCHECK}:\{name.name()}");
    }
}
