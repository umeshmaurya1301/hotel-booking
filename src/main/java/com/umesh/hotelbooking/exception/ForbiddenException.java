package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown by {@link com.umesh.hotelbooking.web.RoleInterceptor} when the caller-declared
 * {@code X-Role} header does not match the route's {@link com.umesh.hotelbooking.web.RequireRole}.
 * Part of the trivially-stubbed role separation of design doc 11.4 — authorisation itself is
 * out of scope, so this only demonstrates that the roles are structurally distinct.
 */
public final class ForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
