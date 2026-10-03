package com.telecom.orders.exception;

/** HTTP 504 - the order workflow passed its deadline before persisting. */
public class WorkflowDeadlineException extends RuntimeException {
    public WorkflowDeadlineException(String message) {
        super(message);
    }
}
