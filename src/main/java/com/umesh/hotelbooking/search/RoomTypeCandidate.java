package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.entity.RoomType;

import java.math.BigDecimal;

/**
 * One still-alive room type inside a {@link SearchCandidate}, carrying the two values only an
 * inventory-touching filter can compute: {@link #stayTotal()} (design doc 10.1's stay-total
 * price, not a nightly rate) and {@link #availableUnits()} (the minimum free-unit count across
 * every requested night). Both are set by {@code AvailabilityFilter} — see its Javadoc and
 * {@code PriceRangeFilter}'s for why the latter also owns the price.
 *
 * <p>Mutable, per-request scratch state — never cached, shared or reused across searches.
 */
public final class RoomTypeCandidate {

    private final RoomType roomType;
    private BigDecimal stayTotal;
    private Integer availableUnits;

    public RoomTypeCandidate(RoomType roomType) {
        this.roomType = roomType;
    }

    public RoomType roomType() {
        return roomType;
    }

    public BigDecimal stayTotal() {
        return stayTotal;
    }

    public void setStayTotal(BigDecimal stayTotal) {
        this.stayTotal = stayTotal;
    }

    public Integer availableUnits() {
        return availableUnits;
    }

    public void setAvailableUnits(Integer availableUnits) {
        this.availableUnits = availableUnits;
    }
}
