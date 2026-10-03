package com.telecom.orders.dto;

import com.telecom.orders.entity.CustomerOrder;
import com.telecom.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, String customerName, String email, String planCode, BigDecimal monthlyPrice,
                            String circle, String msisdn, String iccid, OrderStatus status, Integer creditScore,
                            String reason, Instant createdAt, Instant updatedAt) {

    public static OrderResponse from(CustomerOrder o) {
        return new OrderResponse(o.getId(), o.getCustomerName(), o.getEmail(), o.getPlanCode(), o.getMonthlyPrice(),
                o.getCircle(), o.getMsisdn(), o.getIccid(), o.getStatus(), o.getCreditScore(), o.getReason(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
