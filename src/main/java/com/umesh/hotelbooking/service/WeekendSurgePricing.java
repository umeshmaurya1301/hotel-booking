package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.PricingProperties;
import com.umesh.hotelbooking.entity.RoomType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Applies a configured multiplier on configured weekend days (Friday and Saturday by
 * default), and the base rate on every other night.
 *
 * <p>Which days count as "the weekend" is configuration rather than a hard-coded
 * {@code DayOfWeek.SATURDAY}: it varies by market, and a hotel in a Sunday-Thursday weekend
 * region should not require a code change.
 */
@Component
public class WeekendSurgePricing implements PricingStrategy {

    public static final String CODE = "WEEKEND_SURGE";

    private final PricingProperties pricingProperties;

    public WeekendSurgePricing(PricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    @Override
    public BigDecimal priceFor(RoomType roomType, LocalDate stayDate) {
        BigDecimal base = roomType.getBasePricePerNight();
        PricingProperties.Weekend weekend = pricingProperties.weekend();
        BigDecimal price = weekend.appliesTo(stayDate) ? base.multiply(weekend.multiplier()) : base;
        return price.setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override
    public String strategyCode() {
        return CODE;
    }
}
