package com.umesh.hotelbooking.dto;

/**
 * Implemented by a response DTO whose outcome may not yet be observed (design doc 7.2, 11.2).
 *
 * <p>{@link com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice} checks this
 * rather than sniffing field names reflectively or importing a state enum from {@code entity}
 * — the decision belongs on the DTO that owns the state, which is what stops the advice from
 * growing a new {@code switch} for every future kind of pending outcome.
 */
public interface PendingAware {

    /** @return true if this response's outcome is not yet known and should render as PENDING */
    boolean pending();
}
