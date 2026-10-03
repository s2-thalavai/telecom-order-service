package com.telecom.orders.exception;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Handles DeferredResult.setErrorResult(...) and failed CompletableFutures exactly like synchronous
 * exceptions: Spring unwraps CompletionException for controller return values.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<ProblemDetail> downstream(DownstreamException ex) {
        return retryLater(HttpStatus.SERVICE_UNAVAILABLE, "Downstream Unavailable", ex.getMessage());
    }

    @ExceptionHandler({TimeoutException.class, WorkflowDeadlineException.class})
    public ProblemDetail deadline(Exception ex) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Deadline Exceeded", "The order workflow did not finish in time");
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ProblemDetail> busy(TaskRejectedException ex) {
        return retryLater(HttpStatus.SERVICE_UNAVAILABLE, "Server Busy", "Too many concurrent requests");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail notFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(UnknownPlanException.class)
    public ProblemDetail unknownPlan(UnknownPlanException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown Plan", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        headers.set(HttpHeaders.RETRY_AFTER, "5");
        return handleExceptionInternal(ex, problem(HttpStatus.SERVICE_UNAVAILABLE, "Request Timeout",
                "No result within the request timeout"), headers, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid Request", "Validation failed");
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ResponseEntity<ProblemDetail> retryLater(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status).header(HttpHeaders.RETRY_AFTER, "5").body(problem(status, title, detail));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
