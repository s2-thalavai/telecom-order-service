package com.telecom.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.orders.client.simulated.SimulatedNumberInventory;
import com.telecom.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The CompletableFuture workflow end to end, including its failure modes. */
class OrderWorkflowIT extends AbstractOrderIT {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private SimulatedNumberInventory inventory;

    @Test
    void approvedOrderIsCreatedThenActivatedByTheProvisioningCallback() {
        ResponseEntity<JsonNode> created = placeOrder(email("anita"), "UNL-599");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/v1/orders/" + created.getBody().get("id").asText());
        assertThat(created.getBody().get("status").asText()).isEqualTo("PROVISIONING");
        assertThat(created.getBody().get("msisdn").asText()).startsWith("7");

        JsonNode active = await(id(created.getBody()), 5).getBody();
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(active.get("iccid").asText()).startsWith("8991");
    }

    @Test
    void lowCreditScoreIsRejectedWithoutANumber() {
        JsonNode rejected = place(email("risky"));

        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.has("msisdn")).isFalse();
        assertThat(await(id(rejected), 5).getStatusCode()).isEqualTo(HttpStatus.OK);   // already final
    }

    @Test
    void unavailableCreditBureauIs503() {
        ResponseEntity<JsonNode> response = placeOrder(email("down"), "UNL-599");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }

    @Test
    void unknownPlanIs422AndInvalidInputIs400() {
        assertThat(placeOrder(email("plan"), "XYZ-111").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(placeOrder("not-an-email", "UNL-599").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void slowWorkflowIs504AndLeavesNothingBehind() {
        String email = email("slow");
        int reservedBefore = inventory.reservedCount();

        long started = System.nanoTime();
        ResponseEntity<JsonNode> response = placeOrder(email, "UNL-599");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(elapsedMs).isLessThan(2_000);                         // 1 s deadline, not the 2.5 s credit check

        // the credit check still finishes in the background (2.5 s); the deadline guard must stop the workflow
        // before persisting, and release the number it reserved
        await().pollDelay(Duration.ofMillis(3_500)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(orders.findByEmail(email)).isEmpty();
            assertThat(inventory.reservedCount()).isEqualTo(reservedBefore);
        });
    }

    @Test
    void concurrentOrdersGetDistinctNumbers() {
        List<CompletableFuture<ResponseEntity<JsonNode>>> calls = IntStream.range(0, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> placeOrder(email("bulk"), "DATA-299")))
                .toList();

        List<String> numbers = calls.stream().map(CompletableFuture::join)
                .peek(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .map(r -> r.getBody().get("msisdn").asText())
                .toList();

        assertThat(numbers).doesNotHaveDuplicates().hasSize(10);
    }

    @Test
    void ordersCanBeListedByStatus() {
        place(email("risky"));

        JsonNode page = rest.getForObject("/api/v1/orders?status=REJECTED&size=5", JsonNode.class);

        assertThat(page.get("content").size()).isGreaterThan(0);
        page.get("content").forEach(o -> assertThat(o.get("status").asText()).isEqualTo("REJECTED"));
        assertThat(rest.getForEntity("/api/v1/orders/" + java.util.UUID.randomUUID(), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
