package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects a provider by {@code supports(method, bankCode)} (design doc 6.2).
 *
 * <p>Receives every {@code PaymentGatewayProvider} bean by Spring injection. Adding a
 * provider is registering one more {@code @Component} — this class does not change.
 */
@Component
public class PaymentGatewayRouter {

    private final List<PaymentGatewayProvider> providers;

    public PaymentGatewayRouter(List<PaymentGatewayProvider> providers) {
        this.providers = providers;
    }

    public PaymentGatewayProvider route(PaymentMethod method, String bankCode) {
        return providers.stream()
                .filter(provider -> provider.supports(method, bankCode))
                .findFirst()
                .orElseThrow(() -> new GatewayUnavailableException(
                        "No provider supports " + method + " via bank " + bankCode));
    }

    /** Used by reconciliation, which knows which provider originally handled a payment but not its method. */
    public PaymentGatewayProvider routeByProviderCode(String providerCode) {
        return providers.stream()
                .filter(provider -> provider.providerCode().equals(providerCode))
                .findFirst()
                .orElseThrow(() -> new GatewayUnavailableException(
                        "No provider registered with code " + providerCode));
    }
}
