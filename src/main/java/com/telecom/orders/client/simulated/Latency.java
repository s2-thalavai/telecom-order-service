package com.telecom.orders.client.simulated;

import com.telecom.orders.exception.DownstreamException;

import java.time.Duration;

final class Latency {

    private Latency() {
    }

    static void pause(Duration duration, String system) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamException(system + " call interrupted");
        }
    }
}
