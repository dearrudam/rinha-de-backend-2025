package org.acme.infrastructure;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.domain.DefaultPaymentProcessor;
import org.acme.domain.FallbackPaymentProcessor;
import org.acme.domain.RemotePaymentName;
import org.acme.domain.RemotePaymentProcessorHealth;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Startup
@ApplicationScoped
public class RedisRemoteHealthService {

    public static final String HEALTHCHECK = "healthcheck";
    public static final String HEALTH_LEADER = "health-leader";
    private final String instanceId;
    private final Duration defaultHealthCheckInterval;
    private final DefaultPaymentProcessor defaultPaymentProcessorService;
    private final Duration fallbackHealthCheckInterval;
    private final FallbackPaymentProcessor fallbackPaymentProcessorService;
    private final ExecutorService executorService;
    private final AtomicBoolean isLeader = new AtomicBoolean(Boolean.FALSE);
    private final RedisExecutor redisExecutor;

    @Inject
    public RedisRemoteHealthService(
            RedisExecutor redisExecutor,
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
        this.redisExecutor = redisExecutor;
        this.instanceId = instanceId.orElseGet(() -> UUID.randomUUID().toString());
        this.defaultHealthCheckInterval = defaultHealthCheckInterval;
        this.defaultPaymentProcessorService = defaultPaymentProcessorService;
        this.fallbackHealthCheckInterval = fallbackHealthCheckInterval;
        this.fallbackPaymentProcessorService = fallbackPaymentProcessorService;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Startup
    public void postConstruct() {
        this.executorService.execute(() -> {
            while (true) {
                boolean canBeLeader = redisExecutor.retrieve(
                        ctx -> ctx.jedis().setnx(HEALTH_LEADER, this.instanceId) == 1l);
                if (canBeLeader) {
                    isLeader.set(true);
                    this.executorService.execute(renewLeader());
                    this.executorService.execute(updateStatusTask(RemotePaymentName.DEFAULT));
                    this.executorService.execute(updateStatusTask(RemotePaymentName.FALLBACK));
                }
                try {
                    Thread.sleep(Duration.ofSeconds(3));
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
        });
    }

    private Runnable renewLeader() {
        return () -> {
            while (this.isLeader.get()) {
                try {
                    Thread.sleep(Duration.ofSeconds(9));
                } catch (InterruptedException e) {
                    // do nothing
                }
                try {
                    boolean isLeader = this.instanceId
                            .equalsIgnoreCase(redisExecutor.retrieve(
                                    ctx -> ctx.jedis().get(HEALTH_LEADER)));
                    this.isLeader.set(isLeader);
                    if (isLeader) {
                        redisExecutor.execute(ctx ->
                                ctx.jedis().set(
                                        HEALTH_LEADER,
                                        this.instanceId,
                                        SetParams.setParams()
                                                .ex(10)));
                    }
                } catch (Exception ex) {
                    // do nothing
                }
            }
        };
    }

    private Runnable updateStatusTask(RemotePaymentName name) {
        return () -> {
            while (this.isLeader.get()) {
                updateStatus(name);
            }
        };
    }

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
        } finally {
            sleep(name);
        }
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
        redisExecutor.execute(ctx -> {
            ctx.jedis().set(
                    STR."\{HEALTHCHECK}:\{name.name()}", ctx.encodeToJSON(state));
        });
    }

    public RemotePaymentProcessorHealth getHealth(RedisExecutor.RedisContext ctx, RemotePaymentName name) {
        return ctx.jedis().mget(STR."\{HEALTHCHECK}:\{name.name()}")
                .stream()
                .findFirst()
                .map(ctx.converterFor(RemotePaymentProcessorHealth.class)::apply)
                .orElse(null);
    }
}
