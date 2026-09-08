package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Half the total back if cancelled 24 hours or more before check-in; nothing after that. */
@Component
public class FiftyPercentBefore24Hours implements RefundPolicy {

    public static final String CODE = "FIFTY_PERCENT_BEFORE_24H";
    private static final Duration NOTICE = Duration.ofHours(24);
    private static final BigDecimal FIFTY_PERCENT = new BigDecimal("50");

    @Override
    public BigDecimal calculate(Booking booking, ZoneId propertyZone, Instant cancelledAt) {
        Instant checkInInstant = booking.getCheckIn().atStartOfDay(propertyZone).toInstant();
        boolean withinNotice = !cancelledAt.isAfter(checkInInstant.minus(NOTICE));
        if (!withinNotice) {
            return BigDecimal.ZERO.setScale(booking.getTotalAmount().scale());
        }
        return booking.getTotalAmount()
                .multiply(FIFTY_PERCENT)
                .divide(BigDecimal.valueOf(100), booking.getTotalAmount().scale(), RoundingMode.HALF_EVEN);
    }

    @Override
    public String policyCode() {
        return CODE;
    }
}
