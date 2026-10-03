package com.telecom.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real Tomcat + executors + H2 with the test profile: downstreams 30-50 ms, provisioning 500 ms,
 * slow-delay 2.5 s, workflow deadline 1 s, default await 2 s (max 5 s), bulk item timeout 1 s.
 * Email triggers: slow, down, risky, provfail, manual (no automatic provisioning callback).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractOrderIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;

    /** Unique email so tests never see each other's orders; the tag selects the simulated behaviour. */
    protected static String email(String tag) {
        return tag + "-" + SEQ.incrementAndGet() + "@example.com";
    }

    protected static HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected ResponseEntity<JsonNode> placeOrder(String email, String planCode) {
        return rest.postForEntity("/api/v1/orders",
                json(Map.of("customerName", "IT Customer", "email", email, "planCode", planCode, "circle", "KA")),
                JsonNode.class);
    }

    protected JsonNode place(String email) {
        return placeOrder(email, "UNL-599").getBody();
    }

    protected ResponseEntity<JsonNode> await(UUID id, int timeoutSeconds) {
        return rest.getForEntity("/api/v1/orders/" + id + "/await?timeoutSeconds=" + timeoutSeconds, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> callback(UUID id, boolean success, String iccid) {
        return rest.postForEntity("/api/v1/provisioning/callbacks",
                json(Map.of("orderId", id.toString(), "success", success, "iccid", iccid, "message", "webhook")),
                JsonNode.class);
    }

    protected static UUID id(JsonNode order) {
        return UUID.fromString(order.get("id").asText());
    }
}
