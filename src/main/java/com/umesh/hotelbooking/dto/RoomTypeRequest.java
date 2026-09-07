package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One room type in an onboarding request. {@code basePricePerNight} is the input to the
 * pricing strategy, not necessarily what any given night will cost.
 */
public record RoomTypeRequest(
        @NotBlank String name,
        @Min(1) int totalUnits,
        @Min(1) int maxGuests,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal basePricePerNight) {
}
