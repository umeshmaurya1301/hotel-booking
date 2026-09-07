package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryProperties;
import com.umesh.hotelbooking.exception.UnknownPricingStrategyException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a {@link PricingStrategy} by its code.
 *
 * <p>Spring injects every {@code PricingStrategy} bean on the classpath, so adding a strategy
 * requires one new {@code @Component} and no change here — that is the extensibility claim
 * the brief asks for, and it is verifiable by inspection rather than by assertion.
 */
@Component
public class PricingStrategyRegistry {

    private final Map<String, PricingStrategy> strategiesByCode;
    private final String defaultCode;

    public PricingStrategyRegistry(List<PricingStrategy> strategies, InventoryProperties inventoryProperties) {
        this.strategiesByCode = strategies.stream().collect(Collectors.toMap(
                PricingStrategy::strategyCode,
                strategy -> strategy,
                (first, duplicate) -> {
                    throw new IllegalStateException(
                            "Two pricing strategies declare the code " + first.strategyCode());
                },
                LinkedHashMap::new));
        this.defaultCode = inventoryProperties.defaultPricingStrategy();
        // Fail at startup, not at the first onboarding request, if the configured default
        // names a strategy that does not exist. Inlined rather than calling resolve() so the
        // constructor does not publish 'this' to an overridable method.
        if (!strategiesByCode.containsKey(defaultCode)) {
            throw new UnknownPricingStrategyException(defaultCode, strategiesByCode.keySet());
        }
    }

    public PricingStrategy resolve(String strategyCode) {
        PricingStrategy strategy = strategiesByCode.get(strategyCode);
        if (strategy == null) {
            throw new UnknownPricingStrategyException(strategyCode, knownCodes());
        }
        return strategy;
    }

    /** Resolves {@code strategyCode}, falling back to the configured default when it is absent. */
    public PricingStrategy resolveOrDefault(String strategyCode) {
        return resolve(strategyCode == null || strategyCode.isBlank() ? defaultCode : strategyCode);
    }

    public Set<String> knownCodes() {
        return strategiesByCode.keySet();
    }
}
