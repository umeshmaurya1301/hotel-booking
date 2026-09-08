package com.umesh.hotelbooking.web;

/**
 * The per-request identifiers a controller reads off {@link ApiContext} and passes explicitly
 * into the service layer (design doc 11.3, 8a, 9.6): the client's idempotency key, the
 * server-derived operation type, and the server-generated trace handle.
 *
 * <p>Bundled into one record rather than threading {@code msgId}, {@code apiType} and {@code
 * correlationId} as three parallel {@code String}/enum parameters through {@code
 * BookingService}, {@code PaymentService} and {@code CancellationService} — three parameters
 * repeated across three call chains is the worse shape. A plain record carries no web-scope
 * coupling, so it is exactly as easy to construct with a literal in a service unit test as a
 * bare {@code String msgId} would have been.
 *
 * <p>The reconciliation and sweeper schedulers have no HTTP request in flight, so they build
 * their own {@code RequestMeta} — a generated {@code correlationId} reused across every record
 * one sweep pass writes, and the {@code ApiType} for the operation they are performing.
 */
public record RequestMeta(String msgId, ApiType apiType, String correlationId) {
}
