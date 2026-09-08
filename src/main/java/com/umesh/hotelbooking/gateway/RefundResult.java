package com.umesh.hotelbooking.gateway;

public record RefundResult(GatewayOutcome outcome, String refundReference, String message) {
}
