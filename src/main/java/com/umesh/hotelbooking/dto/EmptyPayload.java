package com.umesh.hotelbooking.dto;

/**
 * Payload for an {@link ApiRequest} that carries no operation-specific fields — the sweeper
 * and reconciliation trigger endpoints, which are state-changing admin operations and belong
 * under idempotency and audit like every other endpoint, but have nothing else to say.
 *
 * <p>Deliberately not {@code ApiRequest<Void>}: {@link ApiRequest#payload()} is {@code
 * @NotNull}, and {@code java.lang.Void} has no non-null instance a client could ever supply —
 * every request would fail validation before reaching the handler. A concrete empty record is
 * the smallest type that satisfies both "no fields" and "constructible", so clients send
 * {@code "payload": {}}.
 */
public record EmptyPayload() {
}
