package com.telecom.orders.service;

import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.dto.BulkEligibilityResult;
import com.telecom.orders.dto.EligibilityLine;
import com.telecom.orders.dto.PortabilityStatus;
import com.telecom.orders.exception.DownstreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkEligibilityServiceTest {

    @Mock
    private IntegrationService integration;

    @Test
    void eachNumberGetsItsOwnOutcomeAndOneFailureNeverFailsTheBatch() {
        BulkEligibilityService service = new BulkEligibilityService(integration, new OrderProperties(
                Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofSeconds(5), 650, Duration.ofMillis(150)));
        when(integration.portabilityCheck("9876543210"))
                .thenReturn(CompletableFuture.completedFuture(new PortabilityStatus("9876543210", true, "eligible")));
        when(integration.portabilityCheck("9000000001"))
                .thenReturn(CompletableFuture.failedFuture(new DownstreamException("MNP gateway unavailable")));
        when(integration.portabilityCheck("9001000001")).thenReturn(new CompletableFuture<>());   // hangs

        BulkEligibilityResult result = service.check(List.of("9876543210", "9000000001", "9001000001")).join();

        assertThat(result.results()).containsExactly(
                new EligibilityLine("9876543210", true, "eligible"),
                EligibilityLine.unknown("9000000001", "MNP gateway unavailable"),
                EligibilityLine.unknown("9001000001", "timeout"));
    }
}
