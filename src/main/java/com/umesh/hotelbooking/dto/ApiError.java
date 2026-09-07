package com.umesh.hotelbooking.dto;

import java.util.List;

/**
 * Error payload returned by the exception handler.
 *
 * <p>{@code code} is a stable, greppable identifier from the {@code DomainException}
 * hierarchy ({@code PROPERTY_NOT_FOUND}, {@code INVENTORY_NOT_MATERIALISED}, …), not free
 * text: clients switch on it, and it survives rewording of the human-readable message.
 *
 * <p>This is the same shape the full request/response envelope will carry in the API phase,
 * so adopting the envelope later does not change what an error body looks like.
 */
public record ApiError(String code, String message, List<FieldError> fieldErrors) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
