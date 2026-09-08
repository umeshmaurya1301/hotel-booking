package com.umesh.hotelbooking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The envelope every {@code /api/v1/**} response is wrapped in by {@code
 * ResponseEnvelopeAdvice} (design doc 11.2). Controllers never build this directly — see the
 * three factories below, which are the only intended callers of the compact constructor.
 *
 * <p>{@code data} and {@code error} are mutually exclusive, enforced here rather than left to
 * caller discipline: a response that is both a success and a failure is a bug in whatever
 * built it, not a state a client should ever have to interpret.
 *
 * @param msgId echoed from the request for client-side correlation; null if the request body
 *     could not be parsed at all (design doc 11, {@code RequestEnvelopeAdvice})
 * @param correlationId server-generated trace handle, always present
 * @param status SUCCESS, FAILURE or PENDING (see {@link ResponseStatus})
 * @param data the operation-specific payload, present on SUCCESS and PENDING
 * @param error present only on FAILURE
 * @param respondedAt server clock at the time this envelope was built
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String msgId,
        String correlationId,
        ResponseStatus status,
        T data,
        ApiError error,
        Instant respondedAt) {

    public ApiResponse {
        if (data != null && error != null) {
            throw new IllegalArgumentException("An ApiResponse cannot carry both data and error");
        }
        if (status == ResponseStatus.FAILURE && error == null) {
            throw new IllegalArgumentException("A FAILURE response must carry an error");
        }
    }

    public static <T> ApiResponse<T> success(String msgId, String correlationId, T data, Instant at) {
        return new ApiResponse<>(msgId, correlationId, ResponseStatus.SUCCESS, data, null, at);
    }

    public static <T> ApiResponse<T> pending(String msgId, String correlationId, T data, Instant at) {
        return new ApiResponse<>(msgId, correlationId, ResponseStatus.PENDING, data, null, at);
    }

    public static <T> ApiResponse<T> failure(String msgId, String correlationId, ApiError error, Instant at) {
        return new ApiResponse<>(msgId, correlationId, ResponseStatus.FAILURE, null, error, at);
    }
}
