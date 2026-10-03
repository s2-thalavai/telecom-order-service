package com.telecom.orders.dto;

public record PortabilityStatus(String msisdn, boolean eligible, String reason) {
}
