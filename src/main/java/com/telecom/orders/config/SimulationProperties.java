package com.telecom.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Latency of the simulated downstream systems. Behaviour is triggered by the customer's email (see README). */
@ConfigurationProperties(prefix = "telecom.simulation")
public record SimulationProperties(
        @DefaultValue("400ms") Duration creditLatency,
        @DefaultValue("300ms") Duration catalogLatency,
        @DefaultValue("250ms") Duration inventoryLatency,
        @DefaultValue("400ms") Duration portabilityLatency,
        @DefaultValue("3s") Duration provisioningLatency,
        @DefaultValue("10s") Duration slowDelay) {
}
