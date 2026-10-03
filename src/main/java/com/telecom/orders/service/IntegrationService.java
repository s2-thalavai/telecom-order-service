package com.telecom.orders.service;

import com.telecom.orders.client.CreditBureauClient;
import com.telecom.orders.client.NumberInventoryClient;
import com.telecom.orders.client.PlanCatalogClient;
import com.telecom.orders.client.PortabilityClient;
import com.telecom.orders.dto.CreditDecision;
import com.telecom.orders.dto.PlanDetails;
import com.telecom.orders.dto.PortabilityStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/** Blocking clients wrapped as CompletableFutures on the bounded integration pool. */
@Service
public class IntegrationService {

    private final CreditBureauClient creditBureau;
    private final NumberInventoryClient inventory;
    private final PlanCatalogClient catalog;
    private final PortabilityClient portability;

    public IntegrationService(CreditBureauClient creditBureau, NumberInventoryClient inventory,
                              PlanCatalogClient catalog, PortabilityClient portability) {
        this.creditBureau = creditBureau;
        this.inventory = inventory;
        this.catalog = catalog;
        this.portability = portability;
    }

    @Async("integrationExecutor")
    public CompletableFuture<CreditDecision> creditCheck(String email) {
        return CompletableFuture.completedFuture(creditBureau.assess(email));
    }

    @Async("integrationExecutor")
    public CompletableFuture<String> reserveNumber(String circle) {
        return CompletableFuture.completedFuture(inventory.reserve(circle));
    }

    @Async("integrationExecutor")
    public CompletableFuture<PlanDetails> fetchPlan(String planCode) {
        return CompletableFuture.completedFuture(catalog.find(planCode));
    }

    @Async("integrationExecutor")
    public CompletableFuture<PortabilityStatus> portabilityCheck(String msisdn) {
        return CompletableFuture.completedFuture(portability.check(msisdn));
    }
}
