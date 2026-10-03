package com.telecom.orders.dto;

import java.util.List;

public record BulkEligibilityResult(List<EligibilityLine> results) {
}
