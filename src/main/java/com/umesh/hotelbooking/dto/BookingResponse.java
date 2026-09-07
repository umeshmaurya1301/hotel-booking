package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * A booking as returned to a guest.
 *
 * <p>{@code holdExpiresAt} is deliberately visible: the rooms are held, not sold, until
 * payment completes, and a client that cannot see the deadline cannot warn anyone about it.
 */
public record BookingResponse(
        String bookingUid,
        String guestUid,
        String propertyUid,
        String roomTypeUid,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int units,
        int adults,
        int children,
        BigDecimal totalAmount,
        String currency,
        BookingState state,
        Instant holdExpiresAt,
        Instant createdAt,
        List<BookingLineItemResponse> lineItems) {

    /**
     * Must be called inside the transaction that loaded {@code booking}: the line items are a
     * lazy collection, and mapping them here is what forces the load while a session still
     * exists.
     */
    public static BookingResponse from(Booking booking, String guestUid, String propertyUid, String roomTypeUid) {
        return new BookingResponse(
                booking.getBookingUid(),
                guestUid,
                propertyUid,
                roomTypeUid,
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.nightCount(),
                booking.getUnits(),
                booking.getAdults(),
                booking.getChildren(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getState(),
                booking.getHoldExpiresAt(),
                booking.getCreatedAt(),
                booking.getLineItems().stream()
                        .sorted(Comparator.comparing(item -> item.getStayDate()))
                        .map(BookingLineItemResponse::from)
                        .toList());
    }
}
