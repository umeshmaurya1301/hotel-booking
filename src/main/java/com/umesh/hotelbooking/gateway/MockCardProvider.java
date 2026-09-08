package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.entity.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Mock CARD gateway. Signs and verifies its own way in a real integration — that
 * per-provider variation is the reason {@link PaymentGatewayProvider} declares both
 * {@code initiate} and {@code verifyCallback} on the same interface rather than factoring
 * signing out as something uniform across banks.
 */
@Component
public class MockCardProvider extends AbstractMockProvider {

    @Override
    public boolean supports(PaymentMethod method, String bankCode) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public String providerCode() {
        return "MOCK_CARD";
    }
}
