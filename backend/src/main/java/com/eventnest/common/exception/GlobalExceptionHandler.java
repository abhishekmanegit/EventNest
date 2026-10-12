package com.eventnest.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into one {@link ApiError} with a meaningful status code.
 *
 * <p>Two things to notice here:
 *
 * <ol>
 *   <li><b>The order of the handlers is the design.</b> Expected failures
 *       (AppException, validation) are handled specifically. The
 *       {@code Exception} catch-all is last and is the only one that logs a
 *       stack trace - because anything reaching it is a bug we did not
 *       anticipate, not a condition the user caused.</li>
 *
 *   <li><b>The catch-all returns a generic message.</b> A raw exception message
 *       can contain SQL fragments, column names, or file paths. That is
 *       reconnaissance for an attacker, so it goes to the log, never the
 *       response.</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------ 400 -------

    /**
     * @Valid on a request body failed. This is the one that powers inline form
     * errors on the frontend, so it returns per-field detail rather than one
     * opaque message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Please correct the highlighted fields.", request, violations);
    }

    @ExceptionHandler({BadRequestException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        // Malformed JSON deserves a clearer hint than the default message.
        String message = ex instanceof HttpMessageNotReadableException
                ? "Request body is missing or malformed."
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.BAD_REQUEST,
                "Method " + ex.getMethod() + " is not supported on this endpoint.", request, null);
    }

    // ------------------------------------------------------------ 401/403 ----

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        // The status comes from the ErrorCode, not from the exception subclass, so
        // ErrorCode stays the single source of truth. That lets one code (e.g.
        // ACCOUNT_SUSPENDED) be thrown from paths that should answer 403 and paths
        // that should answer 401, without a second mapping table.
        return build(HttpStatus.valueOf(ex.getErrorCode().httpStatus()),
                ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource.", request, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.valueOf(ex.getErrorCode().httpStatus()),
                ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action.", request, null);
    }

    // ------------------------------------------------------------ 404 -------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI() + ".", request, null);
    }

    // ------------------------------------------------------------ 409 -------

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    /**
     * The database backstop (D8).
     *
     * <p>Our services check for duplicates first so users get a friendly
     * "ALREADY_REGISTERED". But a check-then-insert has a race window, so the
     * UNIQUE constraint is what actually prevents double registration - and this
     * handler is what turns the resulting violation back into that same friendly
     * 409 instead of a 500. Belt and braces: the belt is the one that counts.
     *
     * <p>We deliberately do NOT leak {@code ex.getMostSpecificCause().getMessage()}
     * here - PostgreSQL constraint messages quote table and column names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Database constraint violation on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE,
                "That record already exists or violates a database rule.", request, null);
    }

    // ------------------------------------------------------------ 500 -------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [traceId={}] on {} {}",
                traceId, request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side. Please try again.", request, null, traceId);
    }

    // ---------------------------------------------------------- helpers ------

    private ApiError.FieldViolation toViolation(FieldError error) {
        // Prefer a human message over "must not be blank".
        String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        return new ApiError.FieldViolation(error.getField(), message);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, ErrorCode code, String message,
            HttpServletRequest request, List<ApiError.FieldViolation> violations) {
        return build(status, code, message, request, violations, UUID.randomUUID().toString());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, ErrorCode code, String message,
            HttpServletRequest request, List<ApiError.FieldViolation> violations, String traceId) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock),
                status.value(),
                code.name(),
                message,
                request.getRequestURI(),
                violations,
                traceId));
    }
}
