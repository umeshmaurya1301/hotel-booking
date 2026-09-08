package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.dto.ApiError;
import com.umesh.hotelbooking.dto.ApiResponse;
import com.umesh.hotelbooking.dto.ErrorCode;
import com.umesh.hotelbooking.exception.DomainException;
import com.umesh.hotelbooking.gateway.CircuitBreakerOpenException;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.GatewayUnavailableException;
import com.umesh.hotelbooking.web.ApiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Maps every exception that escapes a controller to an {@link ApiResponse} carrying a {@link
 * ApiError} (design doc 11.2).
 *
 * <p>Status is chosen from the exception's {@link ErrorCode} rather than from its Java type —
 * {@code ErrorCode.status()} — so a new {@code DomainException} carrying an existing code
 * slots into the right status without touching this class or the sets it used to maintain by
 * hand.
 *
 * <p>{@code msgId} and {@code correlationId} are read off {@link ApiContext}: {@code
 * RequestEnvelopeAdvice} has already copied {@code msgId} from the envelope by the time any of
 * these handlers run, except when the body could not be parsed at all, in which case it is
 * legitimately absent.
 *
 * <p>Earlier phases' Javadoc on this class promised that adopting the envelope "will not
 * change this class or any controller." That was half true: no controller's method signature
 * or return type changed, but this class did — it now builds a full {@link ApiResponse}
 * instead of a bare {@link ApiError}, and gained the handlers below it previously lacked.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiContext apiContext;
    private final Clock clock;

    public GlobalExceptionHandler(ApiContext apiContext, Clock clock) {
        this.apiContext = apiContext;
        this.clock = clock;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException exception) {
        return respond(exception.errorCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        // Validating ApiRequest<T>.payload reports the field path as "payload.<field>" — that
        // is where the field actually lives once every state-changing request is enveloped,
        // not a cosmetic wart to strip.
        List<ApiError.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException exception) {
        return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed", List.of());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return respond(ErrorCode.INVALID_REQUEST, "The request could not be read", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException exception) {
        return respond(ErrorCode.INVALID_REQUEST, "No such resource", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return respond(ErrorCode.INVALID_REQUEST, "Method not supported on this route", List.of());
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayTimeout(GatewayTimeoutException exception) {
        return respond(ErrorCode.PAYMENT_TIMEOUT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayUnavailable(GatewayUnavailableException exception) {
        return respond(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, exception.getMessage(), List.of());
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitOpen(CircuitBreakerOpenException exception) {
        return respond(ErrorCode.CIRCUIT_OPEN, exception.getMessage(), List.of());
    }

    /**
     * The security boundary, not a convenience. An exception that reaches here made no promise
     * about what its message contains — unlike {@link DomainException}, which is documented to
     * carry only codes and identifiers — so it routinely holds a SQL fragment, a file path or a
     * parameter value. Logging it (with the correlation id, so it is findable) and returning a
     * generic message is what stops that message becoming an HTTP response body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception [correlationId={}]", apiContext.getCorrelationId(), exception);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        ApiError error = new ApiError(code, message, fieldErrors);
        ApiResponse<Void> response = ApiResponse.failure(
                apiContext.getMsgId(), apiContext.getCorrelationId(), error, Instant.now(clock));
        return ResponseEntity.status(code.status()).body(response);
    }
}
