package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Rolls the materialised inventory window forward (design doc 4.2 — the "job to roll the
 * window forward" that eager materialisation requires).
 *
 * @param propertyUid optional; when absent every property is extended
 * @param horizonDays optional; falls back to {@code inventory.horizon-days}
 * @param pricingStrategyCode optional; falls back to the configured default. Only the newly
 *     created nights are priced by it — existing rows keep their price, including any manual
 *     override an admin has applied.
 */
public record ExtendHorizonRequest(
        String propertyUid,
        @Min(1) @Max(730) Integer horizonDays,
        String pricingStrategyCode) {
}
