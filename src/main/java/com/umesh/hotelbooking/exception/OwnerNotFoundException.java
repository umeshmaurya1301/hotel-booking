package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown when a lookup by business uid finds nothing.
 */
public final class OwnerNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public OwnerNotFoundException(String ownerUid) {
        super(ErrorCode.OWNER_NOT_FOUND, "No owner found with id " + ownerUid);
    }
}
