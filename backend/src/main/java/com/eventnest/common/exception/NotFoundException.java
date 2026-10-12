package com.eventnest.common.exception;

/**
 * 404 - the resource does not exist, or the caller is not allowed to know that
 * it exists.
 *
 * <p>Returning 404 rather than 403 for "this event is a draft belonging to
 * someone else" is deliberate: a 403 would confirm the event exists, which leaks
 * unpublished content to people who should not see it.
 */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
