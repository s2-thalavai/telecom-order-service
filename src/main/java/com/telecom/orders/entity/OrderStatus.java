package com.telecom.orders.entity;

public enum OrderStatus {
    PROVISIONING, ACTIVE, REJECTED, FAILED;

    public boolean isFinal() {
        return this != PROVISIONING;
    }
}
