package com.telecom.orders.service;

import com.telecom.orders.dto.ProvisioningCallback;
import com.telecom.orders.entity.CustomerOrder;
import com.telecom.orders.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static com.telecom.orders.TestOrders.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvisioningCallbackServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final ProvisioningCallback OK = new ProvisioningCallback(ID, true, "8991000000000000001", "done");

    @Mock
    private OrderTransactions orders;

    @InjectMocks
    private ProvisioningCallbackService callbacks;

    @Test
    void firstCallbackIsApplied() {
        when(orders.completeProvisioning(ID, true, OK.iccid(), "done"))
                .thenReturn(Optional.of(order(ID, OrderStatus.ACTIVE, "7000000001")));

        assertThat(callbacks.handle(OK)).isTrue();
    }

    @Test
    void duplicateCallbackIsAcknowledgedButNotApplied() {
        when(orders.completeProvisioning(ID, true, OK.iccid(), "done")).thenReturn(Optional.empty());

        assertThat(callbacks.handle(OK)).isFalse();
    }

    @Test
    void losingAConcurrentRaceIsNotAnError() {
        when(orders.completeProvisioning(ID, true, OK.iccid(), "done"))
                .thenThrow(new ObjectOptimisticLockingFailureException(CustomerOrder.class, ID));

        assertThat(callbacks.handle(OK)).isFalse();
    }
}
