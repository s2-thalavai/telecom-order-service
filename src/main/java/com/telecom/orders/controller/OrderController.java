package com.telecom.orders.controller;

import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.dto.PageResponse;
import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.service.OrderAwaitService;
import com.telecom.orders.service.OrderTransactions;
import com.telecom.orders.service.OrderWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderWorkflowService workflow;
    private final OrderAwaitService awaits;
    private final OrderTransactions orders;
    private final OrderProperties properties;

    public OrderController(OrderWorkflowService workflow, OrderAwaitService awaits, OrderTransactions orders,
                           OrderProperties properties) {
        this.workflow = workflow;
        this.awaits = awaits;
        this.orders = orders;
        this.properties = properties;
    }

    /** CompletableFuture: parallel + dependent steps, persisted, provisioning started. 201 + Location. */
    @PostMapping
    public CompletableFuture<ResponseEntity<OrderResponse>> place(@Valid @RequestBody PlaceOrderRequest request) {
        return workflow.placeOrder(request)
                .thenApply(order -> ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order));
    }

    /** DeferredResult long polling: 200 once final (ACTIVE/FAILED/REJECTED), else 202 + current state. */
    @GetMapping("/{id}/await")
    public DeferredResult<ResponseEntity<OrderResponse>> await(@PathVariable UUID id,
            @RequestParam(required = false) @Min(1) @Max(55) Integer timeoutSeconds) {
        return awaits.await(id, properties.awaitFor(timeoutSeconds));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orders.list(status, pageable);
    }
}
