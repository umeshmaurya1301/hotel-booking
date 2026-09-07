package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.PricingProperties;
import com.umesh.hotelbooking.entity.RoomType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Applies a multiplier for configured date bands (Diwali, New Year), and the base rate
 * outside them.
 *
 * <p>Where two bands overlap the later one in the configured list wins. That is a stated
 * rule rather than an accident of iteration order: overlapping seasons are a normal thing
 * for an operator to configure, and "the more specific override goes last" is the behaviour
 * an operator expects from a list they control.
 */
@Component
public class SeasonalPricing implements PricingStrategy {

    public static final String CODE = "SEASONAL";

    private final PricingProperties pricingProperties;

    public SeasonalPricing(PricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    @Override
    public BigDecimal priceFor(RoomType roomType, LocalDate stayDate) {
        BigDecimal multiplier = BigDecimal.ONE;
        for (PricingProperties.SeasonalBand band : pricingProperties.seasonal()) {
            if (band.covers(stayDate)) {
                multiplier = band.multiplier();
            }
        }
        return roomType.getBasePricePerNight().multiply(multiplier).setScale(2, RoundingMode.HALF_EVEN);
    }

    @Override
    public String strategyCode() {
        return CODE;
    }
}
