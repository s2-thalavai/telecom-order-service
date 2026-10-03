package com.telecom.orders.dto;

public record CreditDecision(int score, boolean approved, String note) {
}
