package com.eventnest.common.exception;

/**
 * 409 - the request is well-formed and permitted, but conflicts with the current
 * state of the resource.
 *
 * <p>This is where "already registered", "registration closed", "event full" and
 * duplicate-constraint violations land. 409 is the honest answer: the client
 * should not simply retry, it needs to change something first.
 */
public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
