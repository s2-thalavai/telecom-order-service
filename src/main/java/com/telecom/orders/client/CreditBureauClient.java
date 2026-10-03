package com.telecom.orders.client;

import com.telecom.orders.dto.CreditDecision;

public interface CreditBureauClient {
    CreditDecision assess(String email);
}
