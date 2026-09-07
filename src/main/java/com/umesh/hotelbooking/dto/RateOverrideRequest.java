package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Overrides one night's rate or unit count for a single room type — the manual lever an
 * operator needs for a one-off event or a block of rooms taken out of service, without
 * touching strategy code.
 *
 * <p>At least one of {@code pricePerUnit} or {@code totalUnits} must be present. Lowering
 * {@code totalUnits} below the units already booked for that night is rejected: the database
 * check constraint would refuse it anyway, and a clear error beats a constraint violation.
 */
public record RateOverrideRequest(
        @NotNull java.time.LocalDate stayDate,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal pricePerUnit,
        @Min(0) Integer totalUnits) {
}
