package com.telecom.orders.dto;

import java.math.BigDecimal;

public record PlanDetails(String code, String name, BigDecimal monthlyPrice) {
}
