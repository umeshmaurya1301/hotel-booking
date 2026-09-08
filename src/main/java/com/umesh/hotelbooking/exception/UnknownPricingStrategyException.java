package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

import java.util.Collection;

/**
 * Thrown when a request names a pricing strategy code that no {@code PricingStrategy} bean
 * declares. Lists the codes that do exist, because the usual cause is a typo and the fix is
 * then obvious from the error alone.
 */
public final class UnknownPricingStrategyException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownPricingStrategyException(String requestedCode, Collection<String> knownCodes) {
        super(ErrorCode.UNKNOWN_PRICING_STRATEGY,
                "No pricing strategy with code " + requestedCode + "; known codes are " + knownCodes);
    }
}
