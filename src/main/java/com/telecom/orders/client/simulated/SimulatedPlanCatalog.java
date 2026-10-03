package com.telecom.orders.client.simulated;

import com.telecom.orders.client.PlanCatalogClient;
import com.telecom.orders.config.SimulationProperties;
import com.telecom.orders.dto.PlanDetails;
import com.telecom.orders.exception.UnknownPlanException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SimulatedPlanCatalog implements PlanCatalogClient {

    private static final Map<String, PlanDetails> PLANS = Map.of(
            "UNL-599", new PlanDetails("UNL-599", "Unlimited 599", new BigDecimal("599.00")),
            "DATA-299", new PlanDetails("DATA-299", "Data 299", new BigDecimal("299.00")),
            "FAM-999", new PlanDetails("FAM-999", "Family 999", new BigDecimal("999.00")));

    private final SimulationProperties sim;
    private final AtomicInteger calls = new AtomicInteger();

    public SimulatedPlanCatalog(SimulationProperties sim) {
        this.sim = sim;
    }

    @Override
    public PlanDetails find(String planCode) {
        calls.incrementAndGet();
        Latency.pause(sim.catalogLatency(), "Plan catalog");
        PlanDetails plan = PLANS.get(planCode);
        if (plan == null) {
            throw new UnknownPlanException("Unknown plan " + planCode);
        }
        return plan;
    }

    /** Number of real catalog calls (shows cache hits). */
    public int calls() {
        return calls.get();
    }
}
