package com.eventnest.common.exception;

/**
 * Base class for every deliberate, expected failure in the application.
 *
 * <p>Throwing a specific subclass instead of {@code ResponseStatusException}
 * everywhere buys us two things:
 * <ul>
 *   <li>The HTTP status and error code travel <em>with</em> the failure, so the
 *       global handler has no per-site switch statement.</li>
 *   <li>Anything that is <em>not</em> an AppException is, by definition, a bug -
 *       and the handler logs it with a stack trace and returns a generic 500.
 *       That clean separation between "expected" and "unexpected" is what keeps
 *       stack traces out of production responses.</li>
 * </ul>
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
