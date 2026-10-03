package com.telecom.orders.service;

import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.event.OrderStatusChangedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Long polling with DeferredResult: "wait until my order is final, at most N seconds".
 * Register first, then re-check: a status change between the first read and the registration is never lost,
 * because the event is delivered only AFTER COMMIT (the re-check sees the committed state).
 */
@Service
public class OrderAwaitService {

    private final ConcurrentHashMap<UUID, Set<DeferredResult<ResponseEntity<OrderResponse>>>> waiters =
            new ConcurrentHashMap<>();
    private final OrderTransactions orders;

    public OrderAwaitService(OrderTransactions orders) {
        this.orders = orders;
    }

    public DeferredResult<ResponseEntity<OrderResponse>> await(UUID orderId, Duration timeout) {
        OrderResponse current = orders.get(orderId);                       // 404 straight away if unknown
        if (current.status().isFinal()) {
            DeferredResult<ResponseEntity<OrderResponse>> done = new DeferredResult<>();
            done.setResult(ResponseEntity.ok(current));
            return done;
        }
        DeferredResult<ResponseEntity<OrderResponse>> result = new DeferredResult<>(timeout.toMillis(),
                () -> ResponseEntity.accepted().body(orders.get(orderId)));   // still provisioning: 202 + state
        waiters.computeIfAbsent(orderId, id -> ConcurrentHashMap.newKeySet()).add(result);
        result.onCompletion(() -> remove(orderId, result));

        OrderResponse latest = orders.get(orderId);                        // re-check AFTER registering
        if (latest.status().isFinal()) {
            result.setResult(ResponseEntity.ok(latest));
        }
        return result;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (!event.order().status().isFinal()) {
            return;
        }
        Set<DeferredResult<ResponseEntity<OrderResponse>>> waiting = waiters.remove(event.order().id());
        if (waiting != null) {
            waiting.forEach(r -> r.setResult(ResponseEntity.ok(event.order())));   // first set wins
        }
    }

    public int waitingFor(UUID orderId) {
        return waiters.getOrDefault(orderId, Set.of()).size();
    }

    private void remove(UUID orderId, DeferredResult<ResponseEntity<OrderResponse>> result) {
        waiters.computeIfPresent(orderId, (id, set) -> {
            set.remove(result);
            return set.isEmpty() ? null : set;
        });
    }
}
