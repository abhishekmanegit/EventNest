package com.eventnest.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The single error shape every failed request returns (ARCHITECTURE.md section 5.8).
 *
 * <p>One consistent error envelope matters far more than one consistent success
 * envelope: the frontend has exactly one function to call when something breaks,
 * and adding a new error type never requires teaching the UI a new shape.
 *
 * @param traceId correlates the response with the server log line. A user can
 *                report "it said 500" and we can find the exact stack trace.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors,
        String traceId) {

    /** One invalid field on a form - rendered inline next to the input. */
    public record FieldViolation(String field, String message) {
    }
}
