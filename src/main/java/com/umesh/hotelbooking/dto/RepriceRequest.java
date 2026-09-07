package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Re-runs a pricing strategy over an existing date range (design doc 4.2.1).
 *
 * <p>This endpoint is what makes {@code PricingStrategy} demonstrably pluggable rather than a
 * one-shot at onboarding: the same room type, repriced under a different strategy, produces
 * different nightly rates without any code change.
 *
 * <p>Repricing changes future rates only. Bookings already taken hold their own per-night
 * price snapshot on their line items, so a reprice can never alter what an existing booking
 * owes.
 *
 * @param from inclusive
 * @param to inclusive
 */
public record RepriceRequest(
        @NotBlank String roomTypeUid,
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @NotBlank String strategyCode) {
}
