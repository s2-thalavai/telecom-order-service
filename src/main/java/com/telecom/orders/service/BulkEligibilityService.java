package com.telecom.orders.service;

import com.telecom.orders.config.OrderProperties;
import com.telecom.orders.dto.BulkEligibilityResult;
import com.telecom.orders.dto.EligibilityLine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Independent checks in parallel; each with its own timeout and fallback, so one slow number never fails all. */
@Service
public class BulkEligibilityService {

    private final IntegrationService integration;
    private final OrderProperties properties;

    public BulkEligibilityService(IntegrationService integration, OrderProperties properties) {
        this.integration = integration;
        this.properties = properties;
    }

    public CompletableFuture<BulkEligibilityResult> check(List<String> msisdns) {
        long timeoutMs = properties.bulkItemTimeout().toMillis();
        List<CompletableFuture<EligibilityLine>> futures = msisdns.stream()
                .map(msisdn -> integration.portabilityCheck(msisdn)                      // all start immediately
                        .thenApply(status -> EligibilityLine.ok(msisdn, status))
                        .completeOnTimeout(EligibilityLine.unknown(msisdn, "timeout"), timeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> EligibilityLine.unknown(msisdn, rootMessage(ex))))
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(done -> new BulkEligibilityResult(futures.stream().map(CompletableFuture::join).toList()));
    }

    static String rootMessage(Throwable ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage();
    }
}
