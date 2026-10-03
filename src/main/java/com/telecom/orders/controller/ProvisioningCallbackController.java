package com.telecom.orders.controller;

import com.telecom.orders.dto.ProvisioningCallback;
import com.telecom.orders.service.ProvisioningCallbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProvisioningCallbackController {

    private final ProvisioningCallbackService callbacks;

    public ProvisioningCallbackController(ProvisioningCallbackService callbacks) {
        this.callbacks = callbacks;
    }

    /** Partner webhook. Duplicates and late callbacks are acknowledged (applied=false) but change nothing. */
    @PostMapping("/api/v1/provisioning/callbacks")
    public Map<String, Boolean> callback(@Valid @RequestBody ProvisioningCallback callback) {
        return Map.of("applied", callbacks.handle(callback));
    }
}
