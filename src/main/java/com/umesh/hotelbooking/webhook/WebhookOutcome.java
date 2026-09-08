package com.umesh.hotelbooking.webhook;

/**
 * How a {@link WebhookEventLog} row was resolved (design doc 12.3). Every inbound callback
 * gets exactly one of these, including the ones rejected before they were ever processed —
 * {@code SIGNATURE_INVALID} and {@code REPLAY_WINDOW_EXCEEDED} are outcomes precisely because
 * a rejected callback is exactly the one a reviewer wants a record of.
 */
public enum WebhookOutcome {
    PROCESSED, DUPLICATE, SIGNATURE_INVALID, REPLAY_WINDOW_EXCEEDED, UNKNOWN_EVENT_TYPE, PROCESSING_FAILED
}
