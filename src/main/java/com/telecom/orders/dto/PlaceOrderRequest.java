package com.telecom.orders.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
        @NotBlank @Size(max = 100) String customerName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "^[A-Z]{3,4}-\\d{3}$") String planCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2,3}$") String circle) {
}
