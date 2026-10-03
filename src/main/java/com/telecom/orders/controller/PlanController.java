package com.telecom.orders.controller;

import com.telecom.orders.dto.PlanDetails;
import com.telecom.orders.service.PlanService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    /** Cached CompletableFuture: the second call for a plan is answered from Caffeine. */
    @GetMapping("/api/v1/plans/{code}")
    public CompletableFuture<PlanDetails> plan(@PathVariable @Pattern(regexp = "^[A-Z]{3,4}-\\d{3}$") String code) {
        return plans.plan(code);
    }
}
