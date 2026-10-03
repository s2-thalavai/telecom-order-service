package com.telecom.orders.repository;

import com.telecom.orders.dto.CreditDecision;
import com.telecom.orders.dto.Eligibility;
import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PlanDetails;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.event.OrderStatusChangedEvent;
import com.telecom.orders.exception.OrderNotFoundException;
import com.telecom.orders.service.OrderTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.UUID;

import static com.telecom.orders.TestOrders.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(OrderTransactions.class)
@RecordApplicationEvents
class OrderTransactionsTest {

    private static final PlanDetails PLAN = new PlanDetails("UNL-599", "Unlimited 599", new BigDecimal("599.00"));

    @Autowired
    private OrderTransactions orderTx;

    @Autowired
    private ApplicationEvents events;

    private OrderResponse provisioningOrder() {
        return orderTx.createOrder(request("anita@example.com"),
                Eligibility.of(new CreditDecision(720, true, null), PLAN).withNumber("7000000001"));
    }

    @Test
    void approvedOrderIsSavedAsProvisioningAndAnnounced() {
        OrderResponse order = provisioningOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PROVISIONING);
        assertThat(order.msisdn()).isEqualTo("7000000001");
        assertThat(order.createdAt()).isNotNull();
        assertThat(events.stream(OrderStatusChangedEvent.class)).hasSize(1);
    }

    @Test
    void declinedOrderIsSavedAsRejectedWithReason() {
        OrderResponse order = orderTx.createOrder(request("risky@example.com"),
                Eligibility.of(new CreditDecision(540, false, "Credit score below threshold"), PLAN));

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.msisdn()).isNull();
        assertThat(order.reason()).isEqualTo("Credit score below threshold");
    }

    @Test
    void provisioningCompletesOnlyOnce() {
        UUID id = provisioningOrder().id();

        assertThat(orderTx.completeProvisioning(id, true, "8991000000000000001", "done"))
                .hasValueSatisfying(o -> assertThat(o.status()).isEqualTo(OrderStatus.ACTIVE));
        assertThat(orderTx.completeProvisioning(id, false, null, "late failure")).isEmpty();
        assertThat(orderTx.get(id).status()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(events.stream(OrderStatusChangedEvent.class)).hasSize(2);      // created + activated
    }

    @Test
    void unknownOrder() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> orderTx.get(id)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> orderTx.completeProvisioning(id, true, null, null))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
