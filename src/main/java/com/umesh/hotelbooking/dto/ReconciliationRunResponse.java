package com.umesh.hotelbooking.dto;

/** Outcome of one reconciliation pass over PAYMENT_UNKNOWN payments (design doc 7.6.4). */
public record ReconciliationRunResponse(
        int checked,
        int settled,
        int failed,
        int movedToManualReview,
        int stillPending,
        int errors,
        int inventoryReleasedOnHoldWindow) {
}
