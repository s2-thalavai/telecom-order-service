package com.telecom.orders.service;

import com.telecom.orders.client.NumberInventoryClient;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.event.OrderStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** The external inventory is told only once the FAILED status is durable. */
@Component
public class NumberReleaseListener {

    private final NumberInventoryClient inventory;

    public NumberReleaseListener(NumberInventoryClient inventory) {
        this.inventory = inventory;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (event.order().status() == OrderStatus.FAILED && event.order().msisdn() != null) {
            inventory.release(event.order().msisdn());
        }
    }
}
