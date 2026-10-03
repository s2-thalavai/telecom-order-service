package com.telecom.orders.exception;

/** HTTP 503 - a downstream system failed. */
public class DownstreamException extends RuntimeException {
    public DownstreamException(String message) {
        super(message);
    }
}
