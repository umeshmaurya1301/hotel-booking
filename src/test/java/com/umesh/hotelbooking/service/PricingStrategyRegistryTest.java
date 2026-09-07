package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryProperties;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.exception.UnknownPricingStrategyException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingStrategyRegistryTest {

    private static final InventoryProperties DEFAULTS = new InventoryProperties(90, "FLAT");

    /** A strategy defined only in this test — proof that adding one needs no registry change. */
    private static final class DoubleEverythingPricing implements PricingStrategy {
        @Override
        public BigDecimal priceFor(RoomType roomType, LocalDate stayDate) {
            return roomType.getBasePricePerNight().multiply(BigDecimal.TWO);
        }

        @Override
        public String strategyCode() {
            return "DOUBLE";
        }
    }

    @Test
    void resolvesEachRegisteredStrategyByCode() {
        PricingStrategyRegistry registry = new PricingStrategyRegistry(
                List.of(new FlatRatePricing(), new DoubleEverythingPricing()), DEFAULTS);

        assertThat(registry.resolve("FLAT")).isInstanceOf(FlatRatePricing.class);
        assertThat(registry.resolve("DOUBLE")).isInstanceOf(DoubleEverythingPricing.class);
        assertThat(registry.knownCodes()).containsExactlyInAnyOrder("FLAT", "DOUBLE");
    }

    @Test
    void unknownCodeThrowsAndNamesTheCodesThatDoExist() {
        PricingStrategyRegistry registry = new PricingStrategyRegistry(
                List.of(new FlatRatePricing()), DEFAULTS);

        assertThatThrownBy(() -> registry.resolve("NO_SUCH"))
                .isInstanceOf(UnknownPricingStrategyException.class)
                .hasMessageContaining("NO_SUCH")
                .hasMessageContaining("FLAT");
    }

    @Test
    void resolveOrDefaultFallsBackToTheConfiguredDefault() {
        PricingStrategyRegistry registry = new PricingStrategyRegistry(
                List.of(new FlatRatePricing(), new DoubleEverythingPricing()), DEFAULTS);

        assertThat(registry.resolveOrDefault(null)).isInstanceOf(FlatRatePricing.class);
        assertThat(registry.resolveOrDefault("  ")).isInstanceOf(FlatRatePricing.class);
        assertThat(registry.resolveOrDefault("DOUBLE")).isInstanceOf(DoubleEverythingPricing.class);
    }

    @Test
    void aDefaultNamingAMissingStrategyFailsAtConstructionNotOnFirstUse() {
        InventoryProperties badDefault = new InventoryProperties(90, "NOT_REGISTERED");

        assertThatThrownBy(() -> new PricingStrategyRegistry(List.of(new FlatRatePricing()), badDefault))
                .isInstanceOf(UnknownPricingStrategyException.class);
    }

    @Test
    void twoStrategiesSharingACodeIsRejected() {
        assertThatThrownBy(() -> new PricingStrategyRegistry(
                List.of(new FlatRatePricing(), new FlatRatePricing()), DEFAULTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FLAT");
    }
}
