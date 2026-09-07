package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Inventory materialisation settings (design doc 4.2).
 *
 * @param horizonDays nights of inventory created ahead at onboarding time. Eager
 *     materialisation over a bounded window: rows always exist, so the availability check is
 *     a plain indexed read and locking is uniform.
 * @param defaultPricingStrategy strategy code applied at materialisation when an onboarding
 *     request does not name one.
 */
@ConfigurationProperties("inventory")
public record InventoryProperties(int horizonDays, String defaultPricingStrategy) {

    public InventoryProperties {
        if (horizonDays < 1) {
            throw new IllegalArgumentException("inventory.horizon-days must be at least 1");
        }
        if (defaultPricingStrategy == null || defaultPricingStrategy.isBlank()) {
            defaultPricingStrategy = "FLAT";
        }
    }
}
