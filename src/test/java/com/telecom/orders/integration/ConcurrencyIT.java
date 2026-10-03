package com.telecom.orders.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.orders.dto.ProvisioningCallback;
import com.telecom.orders.repository.OrderRepository;
import com.telecom.orders.service.ProvisioningCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Partners retry webhooks: concurrent duplicate callbacks must apply exactly once (@Version + state guard). */
class ConcurrencyIT extends AbstractOrderIT {

    @Autowired
    private ProvisioningCallbackService callbacks;

    @Autowired
    private OrderRepository orders;

    @Test
    void concurrentDuplicateCallbacksApplyExactlyOnce() throws Exception {
        UUID id = id(place(email("manual")));
        Set<String> iccids = IntStream.range(0, 10).mapToObj(i -> "89910000000000001%02d".formatted(i))
                .collect(Collectors.toSet());

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (String iccid : iccids) {
            results.add(pool.submit(() -> {
                start.await();
                return callbacks.handle(new ProvisioningCallback(id, true, iccid, "retry"));
            }));
        }
        start.countDown();
        int applied = 0;
        for (Future<Boolean> result : results) {
            applied += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertThat(applied).isEqualTo(1);
        var order = orders.findById(id).orElseThrow();
        assertThat(order.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(iccids).contains(order.getIccid());                      // exactly one winner's data
        assertThat(order.getVersion()).isEqualTo(1L);                       // created (0) + one transition
    }

    @Test
    void successAndFailureRacingEndInExactlyOneFinalState() throws Exception {
        UUID id = id(place(email("manual")));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> success = pool.submit(() -> {
            start.await();
            return callbacks.handle(new ProvisioningCallback(id, true, "8991000000000000999", "ok"));
        });
        Future<Boolean> failure = pool.submit(() -> {
            start.await();
            return callbacks.handle(new ProvisioningCallback(id, false, null, "HLR rejected"));
        });
        start.countDown();

        boolean successApplied = success.get(10, TimeUnit.SECONDS);
        boolean failureApplied = failure.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(successApplied ^ failureApplied).isTrue();               // exactly one wins
        JsonNode order = rest.getForObject("/api/v1/orders/" + id, JsonNode.class);
        assertThat(order.get("status").asText()).isEqualTo(successApplied ? "ACTIVE" : "FAILED");
    }
}
