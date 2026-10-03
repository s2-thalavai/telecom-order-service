package com.telecom.orders.client.simulated;

import com.telecom.orders.client.PortabilityClient;
import com.telecom.orders.config.SimulationProperties;
import com.telecom.orders.dto.PortabilityStatus;
import com.telecom.orders.exception.DownstreamException;
import org.springframework.stereotype.Component;

/** Numbers starting 9000 fail, 9001 are slow; others: even last digit = eligible. */
@Component
public class SimulatedPortability implements PortabilityClient {

    private final SimulationProperties sim;

    public SimulatedPortability(SimulationProperties sim) {
        this.sim = sim;
    }

    @Override
    public PortabilityStatus check(String msisdn) {
        Latency.pause(msisdn.startsWith("9001") ? sim.slowDelay() : sim.portabilityLatency(), "MNP gateway");
        if (msisdn.startsWith("9000")) {
            throw new DownstreamException("MNP gateway unavailable");
        }
        boolean eligible = (msisdn.charAt(msisdn.length() - 1) - '0') % 2 == 0;
        return new PortabilityStatus(msisdn, eligible, eligible ? "eligible" : "ported within the last 90 days");
    }
}
