package com.eventnest.common.exception;

/**
 * 403 - we know who you are, but you are not allowed to do this.
 *
 * <p>The classic case this is for: URL rules can answer "is this user an
 * organizer?" but never "is this user THE organizer of event 42?". That
 * ownership check has to live in the service layer (ARCHITECTURE.md section 5.4).
 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
