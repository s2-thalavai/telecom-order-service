package com.telecom.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.orders.client.simulated.SimulatedNumberInventory;
import com.telecom.orders.service.OrderAwaitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** DeferredResult long polling on real Tomcat: 202 on timeout, completion by webhook and by AFTER_COMMIT events. */
class LongPollIT extends AbstractOrderIT {

    @Autowired
    private OrderAwaitService awaits;

    @Autowired
    private SimulatedNumberInventory inventory;

    @Test
    void waitExpiresWith202AndTheCurrentState() {
        UUID id = id(place(email("manual")));                    // no automatic callback

        long started = System.nanoTime();
        ResponseEntity<JsonNode> response = await(id, 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status").asText()).isEqualTo("PROVISIONING");
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isBetween(900L, 3_000L);
        assertThat(awaits.waitingFor(id)).isZero();              // onCompletion cleaned up the registry
    }

    @Test
    void webhookCompletesAWaitingRequestAfterCommit() {
        UUID id = id(place(email("manual")));
        CompletableFuture<ResponseEntity<JsonNode>> waiting = CompletableFuture.supplyAsync(() -> await(id, 5));
        await().atMost(Duration.ofSeconds(3)).until(() -> awaits.waitingFor(id) == 1);

        ResponseEntity<JsonNode> hook = callback(id, true, "8991000000000000777");

        assertThat(hook.getBody().get("applied").asBoolean()).isTrue();
        ResponseEntity<JsonNode> answered = waiting.join();
        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(answered.getBody().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(answered.getBody().get("iccid").asText()).isEqualTo("8991000000000000777");
    }

    @Test
    void severalClientsWaitingForTheSameOrderAreAllAnswered() {
        UUID id = id(place(email("manual")));
        CompletableFuture<ResponseEntity<JsonNode>> first = CompletableFuture.supplyAsync(() -> await(id, 5));
        CompletableFuture<ResponseEntity<JsonNode>> second = CompletableFuture.supplyAsync(() -> await(id, 5));
        await().atMost(Duration.ofSeconds(3)).until(() -> awaits.waitingFor(id) == 2);

        callback(id, true, "8991000000000000888");

        assertThat(first.join().getBody().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(second.join().getBody().get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void failedProvisioningReleasesTheNumberAfterCommit() {
        JsonNode order = place(email("provfail"));
        String msisdn = order.get("msisdn").asText();
        assertThat(inventory.isReserved(msisdn)).isTrue();

        JsonNode failed = await(id(order), 5).getBody();

        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        await().atMost(Duration.ofSeconds(2)).until(() -> !inventory.isReserved(msisdn));
    }

    @Test
    void activatedOrderKeepsItsNumberAndLateCallbacksAreIgnored() {
        JsonNode order = place(email("happy"));
        UUID id = id(order);

        assertThat(await(id, 5).getBody().get("status").asText()).isEqualTo("ACTIVE");
        ResponseEntity<JsonNode> late = callback(id, false, null);

        assertThat(late.getBody().get("applied").asBoolean()).isFalse();
        assertThat(rest.getForObject("/api/v1/orders/" + id, JsonNode.class).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(inventory.isReserved(order.get("msisdn").asText())).isTrue();
    }

    @Test
    void awaitingAnUnknownOrderIs404() {
        assertThat(await(UUID.randomUUID(), 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
