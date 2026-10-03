package com.telecom.orders.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProvisioningCallback(@NotNull UUID orderId, boolean success, String iccid, String message) {
}
