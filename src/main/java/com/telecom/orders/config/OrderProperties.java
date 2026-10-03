package com.telecom.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "telecom.orders")
public record OrderProperties(
        @DefaultValue("5s") Duration workflowDeadline,
        @DefaultValue("20s") Duration defaultAwait,
        @DefaultValue("55s") Duration maxAwait,
        @DefaultValue("650") int minimumCreditScore,
        @DefaultValue("2s") Duration bulkItemTimeout) {

    /** Requested long-poll time, defaulted and capped. */
    public Duration awaitFor(Integer requestedSeconds) {
        if (requestedSeconds == null) {
            return defaultAwait;
        }
        Duration requested = Duration.ofSeconds(requestedSeconds);
        return requested.compareTo(maxAwait) > 0 ? maxAwait : requested;
    }
}
