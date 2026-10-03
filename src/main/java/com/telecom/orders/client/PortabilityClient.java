package com.telecom.orders.client;

import com.telecom.orders.dto.PortabilityStatus;

public interface PortabilityClient {
    PortabilityStatus check(String msisdn);
}
