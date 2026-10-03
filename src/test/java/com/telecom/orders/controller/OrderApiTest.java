package com.telecom.orders.controller;

import com.telecom.orders.config.AsyncConfig;
import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.dto.BulkEligibilityResult;
import com.telecom.orders.dto.EligibilityLine;
import com.telecom.orders.dto.OrderResponse;
import com.telecom.orders.entity.OrderStatus;
import com.telecom.orders.exception.DownstreamException;
import com.telecom.orders.exception.OrderNotFoundException;
import com.telecom.orders.exception.UnknownPlanException;
import com.telecom.orders.service.BulkEligibilityService;
import com.telecom.orders.service.OrderAwaitService;
import com.telecom.orders.service.OrderTransactions;
import com.telecom.orders.service.OrderWorkflowService;
import com.telecom.orders.service.PlanService;
import com.telecom.orders.service.ProvisioningCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static com.telecom.orders.TestOrders.order;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Async controllers in MockMvc: perform -> asyncStarted, then asyncDispatch(result). */
@WebMvcTest({OrderController.class, ProvisioningCallbackController.class, PlanController.class,
             PortabilityController.class})
@Import(AsyncConfig.class)
@EnableConfigurationProperties(OrderProperties.class)   // slices don't pick up @ConfigurationPropertiesScan types
class OrderApiTest {

    private static final String VALID_ORDER =
            "{\"customerName\":\"Anita\",\"email\":\"anita@example.com\",\"planCode\":\"UNL-599\",\"circle\":\"KA\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderWorkflowService workflow;
    @MockitoBean
    private OrderAwaitService awaits;
    @MockitoBean
    private OrderTransactions orders;
    @MockitoBean
    private ProvisioningCallbackService callbacks;
    @MockitoBean
    private PlanService plans;
    @MockitoBean
    private BulkEligibilityService bulk;

    private MvcResult started(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
    }

    private static MockHttpServletRequestBuilder placeOrder() {
        return post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER);
    }

    @Test
    void completedWorkflowIs201WithLocation() throws Exception {
        OrderResponse created = order(OrderStatus.PROVISIONING);
        when(workflow.placeOrder(any())).thenReturn(CompletableFuture.completedFuture(created));

        mvc.perform(asyncDispatch(started(placeOrder())))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", "/api/v1/orders/" + created.id()))
           .andExpect(jsonPath("$.status").value("PROVISIONING"));
    }

    @Test
    void failedFuturesAreMappedByTheControllerAdvice() throws Exception {
        when(workflow.placeOrder(any())).thenReturn(CompletableFuture.failedFuture(new DownstreamException("bureau down")),
                CompletableFuture.failedFuture(new TimeoutException()),
                CompletableFuture.failedFuture(new UnknownPlanException("Unknown plan XYZ-111")));

        mvc.perform(asyncDispatch(started(placeOrder())))
           .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"));
        mvc.perform(asyncDispatch(started(placeOrder()))).andExpect(status().isGatewayTimeout());
        mvc.perform(asyncDispatch(started(placeOrder()))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidOrderIs400WithoutStartingTheWorkflow() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"A\",\"email\":\"not-an-email\",\"planCode\":\"x\",\"circle\":\"KA\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors.email").exists())
           .andExpect(jsonPath("$.errors.planCode").exists());
        verifyNoInteractions(workflow);
    }

    @Test
    void deferredResultCompletedByAnotherThreadIs200() throws Exception {
        UUID id = UUID.randomUUID();
        DeferredResult<ResponseEntity<OrderResponse>> deferred = new DeferredResult<>(5_000L);
        when(awaits.await(id, Duration.ofSeconds(3))).thenReturn(deferred);
        MvcResult started = started(get("/api/v1/orders/" + id + "/await").param("timeoutSeconds", "3"));

        Thread callback = new Thread(() -> deferred.setResult(ResponseEntity.ok(order(id, OrderStatus.ACTIVE, "7000000001"))));
        callback.start();
        callback.join();

        mvc.perform(asyncDispatch(started))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void stillProvisioningIs202() throws Exception {
        UUID id = UUID.randomUUID();
        DeferredResult<ResponseEntity<OrderResponse>> deferred = new DeferredResult<>(5_000L);
        when(awaits.await(eq(id), any())).thenReturn(deferred);
        MvcResult started = started(get("/api/v1/orders/" + id + "/await"));

        deferred.setResult(ResponseEntity.accepted().body(order(id, OrderStatus.PROVISIONING, "7000000001")));

        mvc.perform(asyncDispatch(started)).andExpect(status().isAccepted());
    }

    @Test
    void awaitingAnUnknownOrderIs404WithoutGoingAsync() throws Exception {
        UUID id = UUID.randomUUID();
        when(awaits.await(eq(id), any())).thenThrow(new OrderNotFoundException(id));

        mvc.perform(get("/api/v1/orders/" + id + "/await"))
           .andExpect(request().asyncNotStarted())
           .andExpect(status().isNotFound());
    }

    @Test
    void awaitTimeoutIsValidated() throws Exception {
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID() + "/await").param("timeoutSeconds", "500"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void webhookReportsWhetherTheCallbackWasApplied() throws Exception {
        when(callbacks.handle(any())).thenReturn(true, false);
        String body = "{\"orderId\":\"" + UUID.randomUUID() + "\",\"success\":true,\"iccid\":\"8991000000000000001\"}";

        mvc.perform(post("/api/v1/provisioning/callbacks").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(jsonPath("$.applied").value(true));
        mvc.perform(post("/api/v1/provisioning/callbacks").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(jsonPath("$.applied").value(false));
    }

    @Test
    void bulkCheckValidatesNumbersAndReturnsResults() throws Exception {
        when(bulk.check(List.of("9876543210"))).thenReturn(CompletableFuture.completedFuture(
                new BulkEligibilityResult(List.of(new EligibilityLine("9876543210", true, "eligible")))));

        mvc.perform(post("/api/v1/portability/bulk-check").contentType(MediaType.APPLICATION_JSON).content("[\"12345\"]"))
           .andExpect(status().isBadRequest());
        mvc.perform(asyncDispatch(started(post("/api/v1/portability/bulk-check")
                        .contentType(MediaType.APPLICATION_JSON).content("[\"9876543210\"]"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.results[0].eligible").value(true));
    }

    @Test
    void planCodeIsValidated() throws Exception {
        mvc.perform(get("/api/v1/plans/not-a-plan")).andExpect(status().isBadRequest());
        verifyNoInteractions(plans);
    }
}
