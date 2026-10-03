package com.telecom.orders.service;

import com.telecom.orders.client.NumberInventoryClient;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.telecom.orders.TestOrders.order;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NumberReleaseListenerTest {

    @Mock
    private NumberInventoryClient inventory;

    @InjectMocks
    private NumberReleaseListener listener;

    @Test
    void failedProvisioningReleasesTheNumber() {
        listener.onStatusChanged(new OrderStatusChangedEvent(order(UUID.randomUUID(), OrderStatus.FAILED, "7000000009")));

        verify(inventory).release("7000000009");
    }

    @Test
    void activeAndRejectedOrdersReleaseNothing() {
        listener.onStatusChanged(new OrderStatusChangedEvent(order(OrderStatus.ACTIVE)));
        listener.onStatusChanged(new OrderStatusChangedEvent(order(OrderStatus.REJECTED)));

        verify(inventory, never()).release(any());
    }
}
