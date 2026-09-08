package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown for a business-rule violation that Bean Validation cannot express on a single
 * field — a required-one-of choice between two fields, or a value that is only invalid
 * relative to persisted state (lowering a room type's unit count below what is already
 * booked, say).
 */
public final class InvalidRequestException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
