package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Onboards one property with its room types (design doc 4.3).
 *
 * <p>Owner and group are each "attach to an existing one, or create one": supply
 * {@code ownerUid} to attach or {@code ownerName} to create, and likewise
 * {@code propertyGroupUid} or nothing at all — an independent hotel with no group named gets
 * a group of exactly one created for it, which is what makes a single property a natural
 * case of the chain structure rather than a special case in the code.
 *
 * @param zoneId IANA zone of the property, e.g. {@code Asia/Kolkata}. Required, because
 *     "what night is it here" cannot be answered without it (design doc 4.5).
 * @param pricingStrategyCode optional; falls back to {@code inventory.default-pricing-strategy}
 */
public record OnboardPropertyRequest(
        String ownerUid,
        String ownerName,
        String ownerEmail,

        String propertyGroupUid,
        String propertyGroupName,
        String settlementBankCode,

        @NotBlank String name,
        @NotBlank String city,
        String locality,
        BigDecimal latitude,
        BigDecimal longitude,
        @Min(1) @Max(5) int starRating,
        @NotBlank String zoneId,
        String currency,
        Set<Amenity> amenities,

        @NotEmpty @Valid List<RoomTypeRequest> roomTypes,
        String pricingStrategyCode) {
}
