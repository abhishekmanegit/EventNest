package com.eventnest.common.exception;

/** 401 - no valid credentials. Deliberately vague by default so it cannot become a user-enumeration oracle. */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
