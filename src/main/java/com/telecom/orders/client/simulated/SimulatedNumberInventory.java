package com.telecom.orders.client.simulated;

import com.telecom.orders.client.NumberInventoryClient;
import com.telecom.orders.config.SimulationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe: an atomic counter hands out unique numbers; reservations live in a concurrent set. */
@Component
public class SimulatedNumberInventory implements NumberInventoryClient {

    private final SimulationProperties sim;
    private final AtomicLong next = new AtomicLong(1);
    private final Set<String> reserved = ConcurrentHashMap.newKeySet();

    public SimulatedNumberInventory(SimulationProperties sim) {
        this.sim = sim;
    }

    @Override
    public String reserve(String circle) {
        Latency.pause(sim.inventoryLatency(), "Number inventory");
        String msisdn = "7%09d".formatted(next.getAndIncrement());
        reserved.add(msisdn);
        return msisdn;
    }

    @Override
    public void release(String msisdn) {
        reserved.remove(msisdn);
    }

    public boolean isReserved(String msisdn) {
        return reserved.contains(msisdn);
    }

    public int reservedCount() {
        return reserved.size();
    }
}
