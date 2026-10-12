package com.eventnest.common.exception;

/**
 * Every error the API returns has a stable, machine-readable code.
 *
 * <p>This exists so the frontend can branch on behaviour instead of matching
 * English strings:
 * <pre>
 *   if (error.code === 'REGISTRATION_CLOSED') showDeadlineBanner();
 * </pre>
 * Renaming a human-readable message then never breaks the UI, and the frontend
 * is not coupled to prose.
 */
public enum ErrorCode {

    VALIDATION_FAILED(400),
    BAD_REQUEST(400),

    UNAUTHORIZED(401),
    INVALID_CREDENTIALS(401),
    INVALID_TOKEN(401),
    TOKEN_EXPIRED(401),

    FORBIDDEN(403),
    ORGANIZER_NOT_APPROVED(403),
    NOT_EVENT_OWNER(403),
    ACCOUNT_SUSPENDED(403),

    NOT_FOUND(404),
    EMAIL_ALREADY_REGISTERED(409),

    DUPLICATE_RESOURCE(409),
    ALREADY_REGISTERED(409),
    REGISTRATION_CLOSED(409),
    EVENT_NOT_PUBLISHED(409),
    EVENT_ALREADY_STARTED(409),
    EVENT_FULL(409),
    CANNOT_CANCEL_REGISTRATION(409),
    INVALID_STATE_TRANSITION(409),

    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
