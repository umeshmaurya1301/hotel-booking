package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.PricingProperties;
import com.umesh.hotelbooking.entity.RoomType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — the strategies are pure functions of a room type, a date and
 * configuration, so none of this needs a Spring context or a database.
 */
class PricingStrategyTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 10);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 13);

    private RoomType roomType(String basePrice) {
        return RoomType.builder()
                .name("Deluxe King")
                .totalUnits(10)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal(basePrice))
                .build();
    }

    private PricingProperties pricingProperties(BigDecimal weekendMultiplier,
                                                List<PricingProperties.SeasonalBand> bands) {
        return new PricingProperties(
                new PricingProperties.Weekend(weekendMultiplier,
                        Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
                bands);
    }

    @Test
    void flatRateChargesTheBasePriceEveryNight() {
        FlatRatePricing strategy = new FlatRatePricing();
        RoomType roomType = roomType("8000.00");

        assertThat(strategy.priceFor(roomType, THURSDAY)).isEqualByComparingTo("8000.00");
        assertThat(strategy.priceFor(roomType, SATURDAY)).isEqualByComparingTo("8000.00");
        assertThat(strategy.strategyCode()).isEqualTo("FLAT");
    }

    @Test
    void flatRateNormalisesScaleToTwo() {
        BigDecimal price = new FlatRatePricing().priceFor(roomType("8000"), THURSDAY);

        assertThat(price.scale()).isEqualTo(2);
    }

    @Test
    void weekendSurgeAppliesOnlyToConfiguredDays() {
        WeekendSurgePricing strategy = new WeekendSurgePricing(
                pricingProperties(new BigDecimal("1.25"), List.of()));
        RoomType roomType = roomType("8000.00");

        assertThat(strategy.priceFor(roomType, THURSDAY)).isEqualByComparingTo("8000.00");
        assertThat(strategy.priceFor(roomType, FRIDAY)).isEqualByComparingTo("10000.00");
        assertThat(strategy.priceFor(roomType, SATURDAY)).isEqualByComparingTo("10000.00");
        assertThat(strategy.priceFor(roomType, SUNDAY)).isEqualByComparingTo("8000.00");
    }

    @Test
    void weekendDaysAreConfigurableNotHardCoded() {
        // A market whose weekend is Sunday: the strategy must follow configuration.
        PricingProperties sundayWeekend = new PricingProperties(
                new PricingProperties.Weekend(new BigDecimal("2.00"), Set.of(DayOfWeek.SUNDAY)),
                List.of());
        WeekendSurgePricing strategy = new WeekendSurgePricing(sundayWeekend);

        assertThat(strategy.priceFor(roomType("1000.00"), SUNDAY)).isEqualByComparingTo("2000.00");
        assertThat(strategy.priceFor(roomType("1000.00"), SATURDAY)).isEqualByComparingTo("1000.00");
    }

    @Test
    void seasonalAppliesMultiplierInsideItsBandOnly() {
        PricingProperties.SeasonalBand diwali = new PricingProperties.SeasonalBand(
                "DIWALI", LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 12), new BigDecimal("1.40"));
        SeasonalPricing strategy = new SeasonalPricing(
                pricingProperties(BigDecimal.ONE, List.of(diwali)));
        RoomType roomType = roomType("5400.00");

        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 11, 5))).isEqualByComparingTo("5400.00");
        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 11, 6))).isEqualByComparingTo("7560.00");
        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 11, 12))).isEqualByComparingTo("7560.00");
        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 11, 13))).isEqualByComparingTo("5400.00");
    }

    @Test
    void seasonalBandsAreInclusiveAtBothEnds() {
        PricingProperties.SeasonalBand oneDay = new PricingProperties.SeasonalBand(
                "SINGLE", THURSDAY, THURSDAY, new BigDecimal("3.00"));
        SeasonalPricing strategy = new SeasonalPricing(pricingProperties(BigDecimal.ONE, List.of(oneDay)));

        assertThat(strategy.priceFor(roomType("100.00"), THURSDAY)).isEqualByComparingTo("300.00");
    }

    @Test
    void laterSeasonalBandWinsWhereTwoOverlap() {
        PricingProperties.SeasonalBand broad = new PricingProperties.SeasonalBand(
                "PEAK_SEASON", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), new BigDecimal("1.20"));
        PricingProperties.SeasonalBand specific = new PricingProperties.SeasonalBand(
                "NEW_YEAR", LocalDate.of(2026, 12, 28), LocalDate.of(2026, 12, 31), new BigDecimal("2.00"));
        SeasonalPricing strategy = new SeasonalPricing(
                pricingProperties(BigDecimal.ONE, List.of(broad, specific)));
        RoomType roomType = roomType("1000.00");

        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 12, 10))).isEqualByComparingTo("1200.00");
        assertThat(strategy.priceFor(roomType, LocalDate.of(2026, 12, 29))).isEqualByComparingTo("2000.00");
    }

    @Test
    void seasonalWithNoBandsConfiguredIsTheBaseRate() {
        SeasonalPricing strategy = new SeasonalPricing(pricingProperties(BigDecimal.ONE, List.of()));

        assertThat(strategy.priceFor(roomType("4000.00"), THURSDAY)).isEqualByComparingTo("4000.00");
    }
}
