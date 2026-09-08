package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown when a request replays a msgId whose original request is still IN_PROGRESS. The
 * caller must wait and check again, not assume failure — the first attempt may yet succeed.
 */
public final class IdempotencyConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String msgId) {
        super(ErrorCode.REQUEST_IN_PROGRESS, "A request with msgId " + msgId + " is already in progress");
    }
}
