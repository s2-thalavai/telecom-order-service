package com.telecom.orders.client;

import com.telecom.orders.dto.PlanDetails;

public interface PlanCatalogClient {
    PlanDetails find(String planCode);
}
