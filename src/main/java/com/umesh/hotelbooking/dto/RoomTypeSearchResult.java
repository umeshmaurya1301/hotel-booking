package com.umesh.hotelbooking.dto;

import java.math.BigDecimal;

/**
 * One bookable room type within a {@link PropertySearchResult} — named explicitly, with its
 * own price and availability, so a guest knows exactly what to pass as {@code roomTypeUid} to
 * {@code POST /api/v1/user/bookings} rather than having to guess which of a property's room
 * types actually matched (design doc 10.1, task spec §1).
 *
 * @param availableUnits the minimum free-unit count across every requested night
 * @param stayTotal {@code sum(pricePerUnit) × units} across the requested nights — a stay
 *     total, never a nightly rate (design doc 10.1)
 */
public record RoomTypeSearchResult(
        String roomTypeUid,
        String name,
        int maxGuests,
        int availableUnits,
        BigDecimal stayTotal,
        String currency) {
}
