package com.telecom.orders.service;

import com.telecom.orders.client.NumberInventoryClient;
import com.telecom.orders.client.ProvisioningGateway;
import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.dto.CreditDecision;
import com.telecom.orders.dto.Eligibility;
import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.dto.PlanDetails;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.exception.DownstreamException;
import com.telecom.orders.exception.WorkflowDeadlineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.telecom.orders.TestOrders.order;
import static com.telecom.orders.TestOrders.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The CompletableFuture chain with mocked async steps and a direct executor: deterministic, no threads. */
@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {

    private static final PlaceOrderRequest REQUEST = request("anita@example.com");
    private static final PlanDetails PLAN = new PlanDetails("UNL-599", "Unlimited 599", new BigDecimal("599.00"));
    private static final CreditDecision APPROVED = new CreditDecision(720, true, null);
    private static final CreditDecision DECLINED = new CreditDecision(540, false, "Credit score below threshold");

    @Mock
    private IntegrationService integration;
    @Mock
    private PlanService plans;
    @Mock
    private OrderTransactions orderTx;
    @Mock
    private NumberInventoryClient inventory;
    @Mock
    private ProvisioningGateway provisioning;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
    private OrderWorkflowService service;

    @BeforeEach
    void setUp() {
        OrderProperties properties = new OrderProperties(Duration.ofMillis(300), Duration.ofSeconds(2),
                Duration.ofSeconds(5), 650, Duration.ofSeconds(1));
        service = new OrderWorkflowService(integration, plans, orderTx, inventory, provisioning, Runnable::run,
                properties, clock);
        when(plans.plan("UNL-599")).thenReturn(CompletableFuture.completedFuture(PLAN));
    }

    @Test
    void approvedOrderReservesPersistsAndStartsProvisioning() throws Exception {
        OrderResponse saved = order(OrderStatus.PROVISIONING);
        when(integration.creditCheck(REQUEST.email())).thenReturn(CompletableFuture.completedFuture(APPROVED));
        when(integration.reserveNumber("KA")).thenReturn(CompletableFuture.completedFuture("7000000001"));
        when(orderTx.createOrder(eq(REQUEST), any(Eligibility.class))).thenReturn(saved);

        assertThat(service.placeOrder(REQUEST).get()).isEqualTo(saved);

        ArgumentCaptor<Eligibility> eligibility = ArgumentCaptor.forClass(Eligibility.class);
        verify(orderTx).createOrder(eq(REQUEST), eligibility.capture());
        assertThat(eligibility.getValue().msisdn()).isEqualTo("7000000001");
        assertThat(eligibility.getValue().plan()).isEqualTo(PLAN);
        verify(provisioning).submit(saved.id(), saved.msisdn(), REQUEST.email());
    }

    @Test
    void declinedOrderIsPersistedAsRejectedWithoutNumberOrProvisioning() throws Exception {
        when(integration.creditCheck(REQUEST.email())).thenReturn(CompletableFuture.completedFuture(DECLINED));
        when(orderTx.createOrder(eq(REQUEST), any(Eligibility.class))).thenReturn(order(OrderStatus.REJECTED));

        assertThat(service.placeOrder(REQUEST).get().status()).isEqualTo(OrderStatus.REJECTED);
        verify(integration, never()).reserveNumber(anyString());
        verify(provisioning, never()).submit(any(), any(), any());
    }

    @Test
    void creditFailureSkipsAllDependentStages() {
        when(integration.creditCheck(REQUEST.email()))
                .thenReturn(CompletableFuture.failedFuture(new DownstreamException("Credit bureau unavailable")));

        assertThatThrownBy(() -> service.placeOrder(REQUEST).get())
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(DownstreamException.class);
        verify(integration, never()).reserveNumber(anyString());
        verify(orderTx, never()).createOrder(any(), any());
    }

    @Test
    void persistFailureReleasesTheReservedNumber() {
        when(integration.creditCheck(REQUEST.email())).thenReturn(CompletableFuture.completedFuture(APPROVED));
        when(integration.reserveNumber("KA")).thenReturn(CompletableFuture.completedFuture("7000000002"));
        when(orderTx.createOrder(any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.placeOrder(REQUEST).get()).hasRootCauseInstanceOf(IllegalStateException.class);
        verify(inventory).release("7000000002");
        verify(provisioning, never()).submit(any(), any(), any());
    }

    @Test
    void lateWorkflowIsAbandonedBeforePersisting() {
        when(integration.creditCheck(REQUEST.email())).thenReturn(CompletableFuture.completedFuture(APPROVED));
        when(integration.reserveNumber("KA")).thenAnswer(inv -> {
            clock.advance(Duration.ofSeconds(1));               // reservation finished after the 300 ms deadline
            return CompletableFuture.completedFuture("7000000003");
        });

        assertThatThrownBy(() -> service.placeOrder(REQUEST).get())
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(WorkflowDeadlineException.class);
        verify(inventory).release("7000000003");
        verify(orderTx, never()).createOrder(any(), any());
    }

    @Test
    void hangingDownstreamHitsTheOverallDeadline() {
        when(integration.creditCheck(REQUEST.email())).thenReturn(new CompletableFuture<>());   // never completes

        assertThatThrownBy(() -> service.placeOrder(REQUEST).get())
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(TimeoutException.class);
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
