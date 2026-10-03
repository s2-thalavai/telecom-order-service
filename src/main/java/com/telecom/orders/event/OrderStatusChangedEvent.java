package com.telecom.orders.event;

import com.telecom.orders.dto.OrderResponse;

/** Immutable snapshot published inside the transaction, delivered to AFTER_COMMIT listeners. */
public record OrderStatusChangedEvent(OrderResponse order) {
}
