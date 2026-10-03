package com.telecom.orders.dto;

/** eligible = null means "unknown" (downstream error or timeout for this number). */
public record EligibilityLine(String msisdn, Boolean eligible, String detail) {

    public static EligibilityLine ok(String msisdn, PortabilityStatus status) {
        return new EligibilityLine(msisdn, status.eligible(), status.reason());
    }

    public static EligibilityLine unknown(String msisdn, String detail) {
        return new EligibilityLine(msisdn, null, detail);
    }
}
