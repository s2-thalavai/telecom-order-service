package com.telecom.orders.service;

import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.event.OrderStatusChangedEvent;
import com.telecom.orders.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.UUID;

import static com.telecom.orders.TestOrders.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAwaitServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Mock
    private OrderTransactions orders;

    @InjectMocks
    private OrderAwaitService awaits;

    @SuppressWarnings("unchecked")
    private static ResponseEntity<OrderResponse> response(DeferredResult<ResponseEntity<OrderResponse>> result) {
        return (ResponseEntity<OrderResponse>) result.getResult();
    }

    @Test
    void alreadyFinalOrderIsAnsweredImmediately() {
        when(orders.get(ID)).thenReturn(order(ID, OrderStatus.ACTIVE, "7000000001"));

        DeferredResult<ResponseEntity<OrderResponse>> result = awaits.await(ID, WAIT);

        assertThat(response(result).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(awaits.waitingFor(ID)).isZero();
    }

    @Test
    void statusChangeBetweenReadAndRegistrationIsNotLost() {
        // first read: PROVISIONING; the order becomes ACTIVE (and its AFTER_COMMIT event fires with no waiter)
        // before we register; the re-check after registering sees ACTIVE
        when(orders.get(ID)).thenReturn(order(ID, OrderStatus.PROVISIONING, "7000000001"),
                order(ID, OrderStatus.ACTIVE, "7000000001"));

        DeferredResult<ResponseEntity<OrderResponse>> result = awaits.await(ID, WAIT);

        assertThat(result.hasResult()).isTrue();
        assertThat(response(result).getBody().status()).isEqualTo(OrderStatus.ACTIVE);
    }

    @Test
    void finalStatusEventCompletesEveryWaiter() {
        when(orders.get(ID)).thenReturn(order(ID, OrderStatus.PROVISIONING, "7000000001"));
        DeferredResult<ResponseEntity<OrderResponse>> first = awaits.await(ID, WAIT);
        DeferredResult<ResponseEntity<OrderResponse>> second = awaits.await(ID, WAIT);
        assertThat(awaits.waitingFor(ID)).isEqualTo(2);

        awaits.onStatusChanged(new OrderStatusChangedEvent(order(ID, OrderStatus.FAILED, "7000000001")));

        assertThat(response(first).getBody().status()).isEqualTo(OrderStatus.FAILED);
        assertThat(response(second).getBody().status()).isEqualTo(OrderStatus.FAILED);
        assertThat(awaits.waitingFor(ID)).isZero();
    }

    @Test
    void nonFinalEventsDoNotCompleteWaiters() {
        when(orders.get(ID)).thenReturn(order(ID, OrderStatus.PROVISIONING, "7000000001"));
        DeferredResult<ResponseEntity<OrderResponse>> result = awaits.await(ID, WAIT);

        awaits.onStatusChanged(new OrderStatusChangedEvent(order(ID, OrderStatus.PROVISIONING, "7000000001")));

        assertThat(result.hasResult()).isFalse();
    }

    @Test
    void unknownOrderFailsSynchronously() {
        when(orders.get(ID)).thenThrow(new OrderNotFoundException(ID));

        assertThatThrownBy(() -> awaits.await(ID, WAIT)).isInstanceOf(OrderNotFoundException.class);
    }
}
