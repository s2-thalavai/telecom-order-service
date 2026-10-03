package com.telecom.orders.client;

public interface NumberInventoryClient {
    String reserve(String circle);

    void release(String msisdn);
}
