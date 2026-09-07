package com.umesh.hotelbooking.exception;

/**
 * Thrown when a lookup by business uid finds nothing.
 */
public final class PropertyNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PropertyNotFoundException(String propertyUid) {
        super("PROPERTY_NOT_FOUND", "No property found with id " + propertyUid);
    }
}
