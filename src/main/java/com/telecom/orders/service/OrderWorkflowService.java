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
import com.telecom.orders.exception.WorkflowDeadlineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * New-connection order as a CompletableFuture workflow:
 *   credit check ∥ plan lookup (thenCombine) → reserve number if approved (thenCompose)
 *   → deadline guard + persist on the integration pool (thenApplyAsync) → submit provisioning (whenComplete)
 *   → overall deadline (orTimeout).
 *
 * orTimeout completes only the FINAL stage; earlier stages keep running. The deadline guard in persist()
 * therefore stops a late workflow before its first irreversible step: nothing is saved, the number is released.
 */
@Service
public class OrderWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowService.class);

    private final IntegrationService integration;
    private final PlanService plans;
    private final OrderTransactions orderTx;
    private final NumberInventoryClient inventory;
    private final ProvisioningGateway provisioning;
    private final Executor integrationExecutor;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderWorkflowService(IntegrationService integration, PlanService plans, OrderTransactions orderTx,
                                NumberInventoryClient inventory, ProvisioningGateway provisioning,
                                @Qualifier("integrationExecutor") Executor integrationExecutor,
                                OrderProperties properties, Clock clock) {
        this.integration = integration;
        this.plans = plans;
        this.orderTx = orderTx;
        this.inventory = inventory;
        this.provisioning = provisioning;
        this.integrationExecutor = integrationExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    public CompletableFuture<OrderResponse> placeOrder(PlaceOrderRequest request) {
        Instant deadline = clock.instant().plus(properties.workflowDeadline());
        CompletableFuture<CreditDecision> credit = integration.creditCheck(request.email());   // starts now
        CompletableFuture<PlanDetails> plan = plans.plan(request.planCode());                  // starts now (cached)

        return credit
                .thenCombine(plan, Eligibility::of)                                     // both, in parallel
                .thenCompose(eligibility -> eligibility.approved()                       // dependent step
                        ? integration.reserveNumber(request.circle()).thenApply(eligibility::withNumber)
                        : CompletableFuture.completedFuture(eligibility))
                .thenApplyAsync(eligibility -> persist(request, eligibility, deadline), integrationExecutor)
                .whenComplete((order, ex) -> {                                           // side effect after commit
                    if (ex == null && order.status() == OrderStatus.PROVISIONING) {
                        provisioning.submit(order.id(), order.msisdn(), request.email());
                    }
                })
                .orTimeout(properties.workflowDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Deadline guard + compensation: a reserved number must not leak, a late order must not be created. */
    private OrderResponse persist(PlaceOrderRequest request, Eligibility eligibility, Instant deadline) {
        if (clock.instant().isAfter(deadline)) {
            release(eligibility);
            log.warn("Order for {} abandoned: workflow passed its deadline before persisting", request.email());
            throw new WorkflowDeadlineException("Workflow deadline passed before the order was saved");
        }
        try {
            return orderTx.createOrder(request, eligibility);
        } catch (RuntimeException ex) {
            release(eligibility);
            throw ex;
        }
    }

    private void release(Eligibility eligibility) {
        if (eligibility.msisdn() != null) {
            inventory.release(eligibility.msisdn());
        }
    }
}
