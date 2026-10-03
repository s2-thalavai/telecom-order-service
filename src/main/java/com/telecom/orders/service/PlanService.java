package com.telecom.orders.service;

import com.telecom.orders.config.CacheConfig;
import com.telecom.orders.dto.PlanDetails;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PlanService {

    private final IntegrationService integration;

    public PlanService(IntegrationService integration) {
        this.integration = integration;
    }

    /** Hit: completed future, no thread hop. Miss: @Async fetch (separate bean); cached on completion. */
    @Cacheable(cacheNames = CacheConfig.PLANS, key = "#planCode")
    public CompletableFuture<PlanDetails> plan(String planCode) {
        return integration.fetchPlan(planCode);
    }
}
