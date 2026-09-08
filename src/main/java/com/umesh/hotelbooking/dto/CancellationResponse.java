package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.RefundState;

import java.math.BigDecimal;

public record CancellationResponse(
        String bookingUid,
        BookingState bookingState,
        String refundUid,
        BigDecimal refundAmount,
        String currency,
        RefundState refundState) {
}
