package com.umesh.hotelbooking.domain.model.booking;

import com.umesh.hotelbooking.domain.vo.Money;
import com.umesh.hotelbooking.domain.vo.UnitCount;

import java.time.LocalDate;

/**
 * The price for one night of one booking, snapshotted at booking time and never recomputed.
 * A later rate change on {@code daily_inventory} must not alter what an existing booking
 * owes, and a per-night snapshot is what makes a partial (unused-nights) refund calculable —
 * a bare total amount could not answer either question.
 */
public record BookingLineItem(LocalDate stayDate, UnitCount units, Money pricePerUnit, Money lineTotal) {

    public BookingLineItem {
        if (stayDate == null) {
            throw new IllegalArgumentException("stayDate must not be null");
        }
        if (units == null || pricePerUnit == null || lineTotal == null) {
            throw new IllegalArgumentException("units, pricePerUnit and lineTotal must not be null");
        }
        Money expected = pricePerUnit.multiply(units.value());
        if (!expected.equals(lineTotal)) {
            throw new IllegalArgumentException(
                    "lineTotal " + lineTotal + " does not equal pricePerUnit * units (" + expected + ")");
        }
    }
}
