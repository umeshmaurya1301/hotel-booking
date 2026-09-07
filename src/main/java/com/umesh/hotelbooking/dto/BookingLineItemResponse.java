package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.BookingLineItem;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One night of a booking at the price captured when it was made. Exposed rather than
 * summarised so a guest can see why a total is what it is — the answer to "why is this
 * ₹34,000?" is these rows.
 */
public record BookingLineItemResponse(
        LocalDate stayDate,
        int units,
        BigDecimal pricePerUnit,
        BigDecimal lineTotal) {

    public static BookingLineItemResponse from(BookingLineItem item) {
        return new BookingLineItemResponse(
                item.getStayDate(), item.getUnits(), item.getPricePerUnit(), item.getLineTotal());
    }
}
