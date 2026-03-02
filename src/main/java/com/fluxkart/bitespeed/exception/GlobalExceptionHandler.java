package com.fluxkart.bitespeed.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for the REST API.
 * <p>
 * Intercepts exceptions thrown by controllers and maps them to
 * structured JSON error responses with appropriate HTTP status codes.
 * </p>
 *
 * @author fl4nk3r
 * @version 1.0
 * @since 2026-03-01
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles bean-validation failures (e.g. both email and phoneNumber
     * are {@code null} or blank).
     *
     * @param ex the validation exception containing binding errors
     * @return a 400 Bad Request response with a descriptive error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(err -> err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", errors);
        return buildError(HttpStatus.BAD_REQUEST, errors);
    }

    /**
     * Catch-all handler for unexpected server errors.
     * <p>
     * Logs the full stack trace at ERROR level and returns a generic
     * 500 Internal Server Error response to the client.
     * </p>
     *
     * @param ex the unhandled exception
     * @return a 500 Internal Server Error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Builds a structured error response body.
     *
     * @param status  the HTTP status to return
     * @param message a human-readable error description
     * @return a {@link ResponseEntity} containing a timestamp, status, error, and
     *         message
     */
    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
