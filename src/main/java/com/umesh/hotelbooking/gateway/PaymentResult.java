package com.umesh.hotelbooking.gateway;

public record PaymentResult(GatewayOutcome outcome, String providerReference, String message) {
}
