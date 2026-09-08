package com.umesh.hotelbooking.gateway;

public record ReversalResult(GatewayOutcome outcome, String reversalReference, String message) {
}
