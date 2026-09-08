package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown when a lookup by business uid finds nothing.
 */
public final class PropertyNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PropertyNotFoundException(String propertyUid) {
        super(ErrorCode.PROPERTY_NOT_FOUND, "No property found with id " + propertyUid);
    }
}
