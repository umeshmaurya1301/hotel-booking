package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Tuning for the pricing strategies (design doc 4.2.1). Kept in configuration rather than in
 * code so that a rate change is a deployment concern, not a code change.
 *
 * @param weekend multiplier and the days it applies to, for {@code WeekendSurgePricing}
 * @param seasonal date bands and their multipliers, for {@code SeasonalPricing}; later bands
 *     win where two overlap, so the list order is meaningful
 */
@ConfigurationProperties("pricing")
public record PricingProperties(Weekend weekend, List<SeasonalBand> seasonal) {

    public PricingProperties {
        if (weekend == null) {
            weekend = new Weekend(BigDecimal.ONE, Set.of());
        }
        seasonal = seasonal == null ? List.of() : List.copyOf(seasonal);
    }

    public record Weekend(BigDecimal multiplier, Set<DayOfWeek> days) {

        public Weekend {
            multiplier = multiplier == null ? BigDecimal.ONE : multiplier;
            days = days == null ? Set.of() : Set.copyOf(days);
        }

        public boolean appliesTo(LocalDate date) {
            return days.contains(date.getDayOfWeek());
        }
    }

    /** {@code from} and {@code to} are both inclusive - a season is a range of nights. */
    public record SeasonalBand(String label, LocalDate from, LocalDate to, BigDecimal multiplier) {

        public boolean covers(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }
}
