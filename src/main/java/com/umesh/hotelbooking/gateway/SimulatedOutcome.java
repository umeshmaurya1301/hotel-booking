package com.umesh.hotelbooking.gateway;

/**
 * A demo/testing lever with no real-world counterpart: since every provider here is a mock
 * standing in for a bank that does not exist, something has to decide what "the bank" does.
 * This is that something, supplied by the caller instead of left to chance, the same way
 * real payment sandboxes offer specific test cards for specific outcomes.
 *
 * <ul>
 *   <li>{@code SETTLED} (default) — succeeds immediately.</li>
 *   <li>{@code FAILED} — declines immediately. A normal returned outcome, not an exception:
 *       a decline is a successful call with a negative business result and must never trip
 *       the circuit breaker (design doc 7.1's {@code ignoreExceptions} point).</li>
 *   <li>{@code TIMEOUT} — every call hangs past the configured timeout, so the breaker sees
 *       real failures and opens once the threshold is crossed.</li>
 *   <li>{@code STUCK_FOREVER} — initiate returns PENDING and every status poll keeps
 *       returning PENDING, driving the ladder to exhaustion and MANUAL_REVIEW.</li>
 *   <li>{@code STUCK_THEN_SETTLE} / {@code STUCK_THEN_FAIL} — initiate returns PENDING; the
 *       first status poll still returns PENDING, the next resolves. Demonstrates the late
 *       settlement path (7.6.4), including the inventory-released-so-reverse branch if
 *       enough time has passed first.</li>
 * </ul>
 */
public enum SimulatedOutcome {
    SETTLED,
    FAILED,
    TIMEOUT,
    STUCK_FOREVER,
    STUCK_THEN_SETTLE,
    STUCK_THEN_FAIL
}
