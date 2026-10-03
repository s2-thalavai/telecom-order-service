package com.telecom.orders.dto;

/** Immutable intermediate result passed between CompletableFuture stages (safe across threads). */
public record Eligibility(CreditDecision credit, PlanDetails plan, String msisdn) {

    public static Eligibility of(CreditDecision credit, PlanDetails plan) {
        return new Eligibility(credit, plan, null);
    }

    public Eligibility withNumber(String reservedMsisdn) {
        return new Eligibility(credit, plan, reservedMsisdn);
    }

    public boolean approved() {
        return credit.approved();
    }
}
