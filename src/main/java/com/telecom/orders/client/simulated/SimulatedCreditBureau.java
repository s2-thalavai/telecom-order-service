package com.telecom.orders.client.simulated;

import com.telecom.orders.client.CreditBureauClient;
import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.config.SimulationProperties;
import com.telecom.orders.dto.CreditDecision;
import com.telecom.orders.exception.DownstreamException;
import org.springframework.stereotype.Component;

/** Email contains "slow" -> slow, "down" -> unavailable, "risky" -> low score. */
@Component
public class SimulatedCreditBureau implements CreditBureauClient {

    private final SimulationProperties sim;
    private final OrderProperties orders;

    public SimulatedCreditBureau(SimulationProperties sim, OrderProperties orders) {
        this.sim = sim;
        this.orders = orders;
    }

    @Override
    public CreditDecision assess(String email) {
        Latency.pause(email.contains("slow") ? sim.slowDelay() : sim.creditLatency(), "Credit bureau");
        if (email.contains("down")) {
            throw new DownstreamException("Credit bureau unavailable");
        }
        int score = email.contains("risky") ? 540 : 700 + Math.abs(email.hashCode() % 100);
        boolean approved = score >= orders.minimumCreditScore();
        return new CreditDecision(score, approved, approved ? null : "Credit score below threshold");
    }
}
