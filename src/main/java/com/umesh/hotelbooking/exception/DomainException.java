package com.umesh.hotelbooking.exception;

/**
 * Base type for all business-rule failures. Carries a stable {@link #errorCode()} intended
 * for API error responses and log correlation. Exceptions in this hierarchy carry only codes
 * and identifiers, never a full entity or request payload — stack traces get logged, and a
 * payload embedded in a message is a silent data leak.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public final String errorCode() {
        return errorCode;
    }
}
