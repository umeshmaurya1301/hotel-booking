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
 * @param refundPolicyCode optional, and only meaningful when this request creates a new
 *     group (attaching to an existing group keeps that group's existing policy); falls back
 *     to {@code RefundPolicyFactory.DEFAULT_CODE}. Per-group, not per-property, matching
 *     {@code settlementBankCode} (design doc 6.4, 9.3): different chains can carry different
 *     cancellation terms.
 */
public record OnboardPropertyRequest(
        String ownerUid,
        String ownerName,
        String ownerEmail,

        String propertyGroupUid,
        String propertyGroupName,
        String settlementBankCode,
        String refundPolicyCode,

        @NotBlank String name,
        @NotBlank String city,
        String locality,
        BigDecimal latitude,
        BigDecimal longitude,
        @Min(1) @Max(5) int starRating,
        @NotBlank String zoneId,
        String currency,
        Set<Amenity> amenities,

        @NotEmpty List<@Valid RoomTypeRequest> roomTypes,
        String pricingStrategyCode) {
}
