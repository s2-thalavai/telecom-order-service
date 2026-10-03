package com.telecom.orders.service;

import com.telecom.orders.dto.Eligibility;
import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PageResponse;
import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.entity.CustomerOrder;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.event.OrderStatusChangedEvent;
import com.telecom.orders.exception.OrderNotFoundException;
import com.telecom.orders.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The transactional unit of work, called from CompletableFuture stages and provisioning callbacks; each call runs
 * in its own transaction on the calling thread. Status events reach AFTER_COMMIT listeners only if it commits.
 */
@Service
public class OrderTransactions {

    private final OrderRepository orders;
    private final ApplicationEventPublisher events;

    public OrderTransactions(OrderRepository orders, ApplicationEventPublisher events) {
        this.orders = orders;
        this.events = events;
    }

    @Transactional
    public OrderResponse createOrder(PlaceOrderRequest request, Eligibility eligibility) {
        CustomerOrder order = eligibility.approved()
                ? CustomerOrder.provisioning(request, eligibility.plan(), eligibility.credit().score(), eligibility.msisdn())
                : CustomerOrder.rejected(request, eligibility.plan(), eligibility.credit().score(),
                        eligibility.credit().note() != null ? eligibility.credit().note() : "Credit check failed");
        OrderResponse response = OrderResponse.from(orders.saveAndFlush(order));
        events.publishEvent(new OrderStatusChangedEvent(response));
        return response;
    }

    /** @return the new state, or empty if the order was no longer PROVISIONING (duplicate / late callback) */
    @Transactional
    public Optional<OrderResponse> completeProvisioning(UUID orderId, boolean success, String iccid, String message) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PROVISIONING) {
            return Optional.empty();
        }
        if (success) {
            order.activate(iccid);
        } else {
            order.fail(message);
        }
        orders.flush();                                    // surfaces @Version conflicts here, inside the method
        OrderResponse response = OrderResponse.from(order);
        events.publishEvent(new OrderStatusChangedEvent(response));
        return Optional.of(response);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID orderId) {
        return orders.findById(orderId).map(OrderResponse::from).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(OrderStatus status, Pageable pageable) {
        return PageResponse.from((status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable))
                .map(OrderResponse::from));
    }
}
