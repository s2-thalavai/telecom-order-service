package com.telecom.orders.exception;

/** HTTP 422 - the plan code does not exist. */
public class UnknownPlanException extends RuntimeException {
    public UnknownPlanException(String message) {
        super(message);
    }
}
