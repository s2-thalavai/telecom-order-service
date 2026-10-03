package com.telecom.orders.client.simulated;

import com.telecom.orders.client.ProvisioningGateway;
import com.telecom.orders.config.SimulationProperties;
import com.telecom.orders.dto.ProvisioningCallback;
import com.telecom.orders.service.ProvisioningCallbackService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Pretends to be the network provisioning system: accepts immediately, "calls back" later on another thread.
 * Email contains "provfail" -> failure callback; "manual" -> no callback (use POST /api/v1/provisioning/callbacks).
 */
@Component
public class SimulatedProvisioningGateway implements ProvisioningGateway {

    private final ProvisioningCallbackService callbacks;
    private final SimulationProperties sim;
    private final Executor executor;

    public SimulatedProvisioningGateway(ProvisioningCallbackService callbacks, SimulationProperties sim,
                                        @Qualifier("provisioningExecutor") Executor executor) {
        this.callbacks = callbacks;
        this.sim = sim;
        this.executor = executor;
    }

    @Override
    public void submit(UUID orderId, String msisdn, String email) {
        if (email.contains("manual")) {
            return;
        }
        boolean fails = email.contains("provfail");
        ProvisioningCallback callback = new ProvisioningCallback(orderId, !fails,
                fails ? null : iccidFor(orderId), fails ? "HLR rejected the SIM" : "SIM activated");
        CompletableFuture.runAsync(() -> callbacks.handle(callback),
                CompletableFuture.delayedExecutor(sim.provisioningLatency().toMillis(), TimeUnit.MILLISECONDS, executor));
    }

    static String iccidFor(UUID orderId) {
        return "8991" + "%015d".formatted(Math.abs(orderId.getLeastSignificantBits() % 1_000_000_000_000_000L));
    }
}
