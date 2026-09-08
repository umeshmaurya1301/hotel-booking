package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.PaymentState;
import jakarta.validation.constraints.NotNull;

/**
 * Admin resolution of a payment parked in MANUAL_REVIEW (design doc 7.6.3,
 * POST /api/v1/admin/payments/{id}/resolve). {@code outcome} must be SETTLED or FAILED — the
 * two terminal answers a human can give once automation has given up.
 */
public record ResolveManualReviewRequest(@NotNull PaymentState outcome, String reason) {
}
