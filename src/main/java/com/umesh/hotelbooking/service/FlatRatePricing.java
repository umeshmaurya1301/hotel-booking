package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.RoomType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Every night costs the room type's base rate. The baseline strategy and the default.
 */
@Component
public class FlatRatePricing implements PricingStrategy {

    public static final String CODE = "FLAT";

    @Override
    public BigDecimal priceFor(RoomType roomType, LocalDate stayDate) {
        return roomType.getBasePricePerNight().setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override
    public String strategyCode() {
        return CODE;
    }
}
