package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Full refund any time before check-in, with no minimum notice period; nothing once check-in
 * has begun. The most guest-friendly of the three — free cancellation right up to arrival.
 */
@Component
public class NoRefundAfterCheckIn implements RefundPolicy {

    public static final String CODE = "NO_REFUND_AFTER_CHECKIN";

    @Override
    public BigDecimal calculate(Booking booking, ZoneId propertyZone, Instant cancelledAt) {
        Instant checkInInstant = booking.getCheckIn().atStartOfDay(propertyZone).toInstant();
        boolean beforeCheckIn = cancelledAt.isBefore(checkInInstant);
        return beforeCheckIn ? booking.getTotalAmount() : BigDecimal.ZERO.setScale(booking.getTotalAmount().scale());
    }

    @Override
    public String policyCode() {
        return CODE;
    }
}
