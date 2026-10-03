package com.telecom.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.orders.client.simulated.SimulatedPlanCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAndBulkIT extends AbstractOrderIT {

    @Autowired
    private SimulatedPlanCatalog catalog;

    @Test
    void planLookupsAreCachedByValue() {
        rest.getForObject("/api/v1/plans/FAM-999", JsonNode.class);          // populate (may already be cached)
        int calls = catalog.calls();

        JsonNode plan = rest.getForObject("/api/v1/plans/FAM-999", JsonNode.class);
        place(email("anita"));                                               // workflow uses UNL-599 (cached or not)
        rest.getForObject("/api/v1/plans/FAM-999", JsonNode.class);

        assertThat(plan.get("monthlyPrice").decimalValue()).isEqualByComparingTo("999.00");
        assertThat(catalog.calls() - calls).isLessThanOrEqualTo(1);          // FAM-999 never fetched again
    }

    @Test
    void failedLookupsAreNotCached() {
        int calls = catalog.calls();

        ResponseEntity<JsonNode> first = rest.getForEntity("/api/v1/plans/XYZ-111", JsonNode.class);
        rest.getForEntity("/api/v1/plans/XYZ-111", JsonNode.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(catalog.calls() - calls).isEqualTo(2);
    }

    @Test
    void bulkPortabilityChecksRunInParallelWithPerNumberFallbacks() {
        List<String> numbers = List.of("9876543210", "9876543211", "9000000001", "9001000001");

        long started = System.nanoTime();
        JsonNode result = rest.postForObject("/api/v1/portability/bulk-check", json(numbers), JsonNode.class);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        JsonNode lines = result.get("results");
        assertThat(lines.get(0).get("eligible").asBoolean()).isTrue();      // even last digit
        assertThat(lines.get(1).get("eligible").asBoolean()).isFalse();
        assertThat(lines.get(2).get("detail").asText()).isEqualTo("MNP gateway unavailable");
        assertThat(lines.get(3).get("detail").asText()).isEqualTo("timeout");
        assertThat(lines.get(2).has("eligible")).isFalse();                  // unknown
        assertThat(elapsedMs).isLessThan(2_400);                             // 1 s item timeout, not 2.5 s slow call
    }
}
