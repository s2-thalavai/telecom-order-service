package com.telecom.orders.client;

import java.util.UUID;

/** submit() returns immediately; the outcome arrives later as a callback (webhook in production). */
public interface ProvisioningGateway {
    void submit(UUID orderId, String msisdn, String email);
}
