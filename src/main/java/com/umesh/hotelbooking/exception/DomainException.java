package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Base type for all business-rule failures. Carries a stable {@link #errorCode()} — including
 * the HTTP status it maps to — intended for API error responses and log correlation.
 * Exceptions in this hierarchy carry only codes and identifiers, never a full entity or
 * request payload — stack traces get logged, and a payload embedded in a message is a silent
 * data leak.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public final ErrorCode errorCode() {
        return errorCode;
    }
}
