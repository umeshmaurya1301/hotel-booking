package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin-initiated reversal of a settled booking (design doc 9.2's MANUAL_CORRECTION,
 * POST /api/v1/admin/bookings/{id}/reverse) — a duplicate charge or a correction discovered
 * after the fact. Always full and never policy-applied, which is what makes it a reversal
 * and not a refund (9.1); there is no amount field for that reason.
 */
public record ManualReversalRequest(@NotBlank String reason) {
}
