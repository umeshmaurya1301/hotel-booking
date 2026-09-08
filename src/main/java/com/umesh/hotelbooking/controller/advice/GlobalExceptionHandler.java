package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.dto.ApiError;
import com.umesh.hotelbooking.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;

/**
 * Maps exceptions to HTTP status codes and a stable {@link ApiError} body.
 *
 * <p>Status is chosen from the exception's error code rather than from its Java type, so a
 * new exception carrying an existing code slots into the right status without touching this
 * class. The mapping is deliberately coarse — not-found, conflict, or bad request — because
 * finer distinctions would be invented rather than meaningful.
 *
 * <p>The request/response envelope of design doc 11 is a later phase. It arrives as a
 * {@code ResponseBodyAdvice} that wraps whatever controllers already return, so adopting it
 * will not change this class or any controller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> NOT_FOUND_CODES = Set.of(
            "PROPERTY_NOT_FOUND",
            "PROPERTY_GROUP_NOT_FOUND",
            "ROOM_TYPE_NOT_FOUND",
            "OWNER_NOT_FOUND",
            "BOOKING_NOT_FOUND",
            "GUEST_NOT_FOUND",
            "PAYMENT_NOT_FOUND",
            "REFUND_NOT_FOUND",
            "INVENTORY_NOT_MATERIALISED");

    private static final Set<String> CONFLICT_CODES = Set.of(
            "INVENTORY_UNAVAILABLE",
            "INVALID_STATE_TRANSITION",
            "REQUEST_IN_PROGRESS",
            "REFUND_EXCEEDS_CHARGE");

    /** design doc 8a: same msgId, different body — a client bug, distinct from a plain 400. */
    private static final Set<String> UNPROCESSABLE_CODES = Set.of("MSG_ID_PAYLOAD_MISMATCH");

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException exception) {
        HttpStatus status = statusFor(exception.errorCode());
        return ResponseEntity.status(status)
                .body(new ApiError(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed", fieldErrors));
    }

    private static HttpStatus statusFor(String errorCode) {
        if (NOT_FOUND_CODES.contains(errorCode)) {
            return HttpStatus.NOT_FOUND;
        }
        if (CONFLICT_CODES.contains(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        if (UNPROCESSABLE_CODES.contains(errorCode)) {
            return HttpStatus.UNPROCESSABLE_CONTENT; // RFC 9110 rename of 422 Unprocessable Entity
        }
        return HttpStatus.BAD_REQUEST;
    }
}
