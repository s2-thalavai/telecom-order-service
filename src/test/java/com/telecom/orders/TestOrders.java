package com.telecom.orders;

import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestOrders {

    private TestOrders() {
    }

    public static PlaceOrderRequest request(String email) {
        return new PlaceOrderRequest("Test Customer", email, "UNL-599", "KA");
    }

    public static OrderResponse order(UUID id, OrderStatus status, String msisdn) {
        return new OrderResponse(id, "Test Customer", "t@example.com", "UNL-599", new BigDecimal("599.00"), "KA",
                msisdn, status == OrderStatus.ACTIVE ? "8991000000000000001" : null, status, 720, null,
                Instant.now(), null);
    }

    public static OrderResponse order(OrderStatus status) {
        return order(UUID.randomUUID(), status, status == OrderStatus.REJECTED ? null : "7000000001");
    }
}
