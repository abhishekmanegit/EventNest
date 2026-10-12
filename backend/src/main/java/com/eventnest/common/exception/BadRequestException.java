package com.eventnest.common.exception;

/** 400 - the request is malformed or violates a business precondition we can check up front. */
public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
