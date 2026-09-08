package com.umesh.hotelbooking.dto;

/**
 * Outcome carried by {@link ApiResponse#status()}.
 *
 * <p>{@code PENDING} is a first-class outcome, not an afterthought or a soft failure: it is
 * what a request returns when its result is genuinely not yet known — a payment sitting at
 * {@code UNKNOWN} because the gateway never answered, or parked in {@code MANUAL_REVIEW}
 * awaiting a human. It does not mean "processing asynchronously, check back later" — the
 * request has already been fully handled; there is simply no success/failure answer to give
 * yet. Modelling only {@code SUCCESS}/{@code FAILURE} cannot express that, and guessing which
 * of the two it "probably" is would be exactly the mistake design doc 7.2 refuses to make.
 */
public enum ResponseStatus {
    SUCCESS, FAILURE, PENDING
}
