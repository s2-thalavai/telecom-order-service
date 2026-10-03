package com.telecom.orders.controller;

import com.telecom.orders.dto.BulkEligibilityResult;
import com.telecom.orders.service.BulkEligibilityService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
public class PortabilityController {

    private final BulkEligibilityService bulkEligibility;

    public PortabilityController(BulkEligibilityService bulkEligibility) {
        this.bulkEligibility = bulkEligibility;
    }

    /** Up to 50 numbers checked in parallel; per-number timeout and fallback. */
    @PostMapping("/api/v1/portability/bulk-check")
    public CompletableFuture<BulkEligibilityResult> bulkCheck(
            @RequestBody @NotEmpty @Size(max = 50) List<@Pattern(regexp = "^[6-9]\\d{9}$") String> msisdns) {
        return bulkEligibility.check(msisdns);
    }
}
