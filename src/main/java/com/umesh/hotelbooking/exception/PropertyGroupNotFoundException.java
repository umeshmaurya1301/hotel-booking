package com.umesh.hotelbooking.exception;

/**
 * Thrown when a lookup by business uid finds nothing.
 */
public final class PropertyGroupNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PropertyGroupNotFoundException(String propertyGroupUid) {
        super("PROPERTY_GROUP_NOT_FOUND", "No property group found with id " + propertyGroupUid);
    }
}
