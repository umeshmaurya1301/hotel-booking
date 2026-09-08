package com.umesh.hotelbooking.dto;

import java.util.List;

/**
 * Error payload returned by the exception handler and carried in {@link ApiResponse#error()}.
 *
 * <p>{@code code} is a stable, greppable {@link ErrorCode} from the {@code DomainException}
 * hierarchy ({@code PROPERTY_NOT_FOUND}, {@code INVENTORY_NOT_MATERIALISED}, …), not free
 * text: clients switch on it, and it survives rewording of the human-readable message.
 */
public record ApiError(ErrorCode code, String message, List<FieldError> fieldErrors) {

    public ApiError(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
