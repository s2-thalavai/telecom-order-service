package com.telecom.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;

/** Global policy for every DeferredResult/CompletableFuture request: log timeouts and durations. */
public class AsyncObservabilityInterceptor implements DeferredResultProcessingInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AsyncObservabilityInterceptor.class);
    private static final String STARTED = AsyncObservabilityInterceptor.class.getName() + ".started";

    @Override
    public <T> void beforeConcurrentHandling(NativeWebRequest request, DeferredResult<T> result) {
        request.setAttribute(STARTED, System.nanoTime(), RequestAttributes.SCOPE_REQUEST);
    }

    @Override
    public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> result) {
        log.info("Async request timed out: {}", request.getDescription(false));
        return true;                    // continue: the DeferredResult's own timeout result/handler applies
    }

    @Override
    public <T> void afterCompletion(NativeWebRequest request, DeferredResult<T> result) {
        Object started = request.getAttribute(STARTED, RequestAttributes.SCOPE_REQUEST);
        if (started instanceof Long t0) {
            log.debug("Async request {} took {} ms", request.getDescription(false), (System.nanoTime() - t0) / 1_000_000);
        }
    }
}
