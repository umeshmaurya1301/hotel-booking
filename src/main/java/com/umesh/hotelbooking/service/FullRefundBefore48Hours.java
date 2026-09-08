package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Full refund if cancelled 48 hours or more before check-in; nothing after that. The
 * threshold is part of this policy's identity — a different notice period is a different
 * policy (a different class), not a configuration value on this one.
 */
@Component
public class FullRefundBefore48Hours implements RefundPolicy {

    public static final String CODE = "FULL_REFUND_BEFORE_48H";
    private static final Duration NOTICE = Duration.ofHours(48);

    @Override
    public BigDecimal calculate(Booking booking, ZoneId propertyZone, Instant cancelledAt) {
        Instant checkInInstant = booking.getCheckIn().atStartOfDay(propertyZone).toInstant();
        boolean withinNotice = !cancelledAt.isAfter(checkInInstant.minus(NOTICE));
        return withinNotice ? booking.getTotalAmount() : zero(booking);
    }

    private BigDecimal zero(Booking booking) {
        return BigDecimal.ZERO.setScale(booking.getTotalAmount().scale());
    }

    @Override
    public String policyCode() {
        return CODE;
    }
}
