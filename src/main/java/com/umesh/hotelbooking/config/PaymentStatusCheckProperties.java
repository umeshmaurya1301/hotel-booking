package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * The status-check ladder (design doc 7.6.2) — three independent decision points that must
 * not be conflated:
 *
 * @param intervals bounded, escalating delays between reconciliation attempts. Attempts
 *     exhausted (every interval used with no resolution) moves the payment to MANUAL_REVIEW.
 * @param jitterRatio randomises each delay by up to this fraction, so many payments stuck at
 *     the same attempt number do not all poll the gateway in the same instant
 * @param inventoryHoldWindow how long after a payment goes UNKNOWN its room-nights stay held
 *     before being released for sale — decoupled from both the ladder and the deadline below
 * @param autoReversalDeadline how long a payment can sit at PENDING before failure is
 *     presumed and the customer is notified; any later settlement would need a reversal
 */
@ConfigurationProperties("payment.status-check")
public record PaymentStatusCheckProperties(
        List<Duration> intervals,
        double jitterRatio,
        Duration inventoryHoldWindow,
        Duration autoReversalDeadline) {

    public PaymentStatusCheckProperties {
        intervals = (intervals == null || intervals.isEmpty())
                ? List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                        Duration.ofMinutes(5), Duration.ofMinutes(7), Duration.ofMinutes(15),
                        Duration.ofMinutes(30), Duration.ofHours(1))
                : List.copyOf(intervals);
        jitterRatio = jitterRatio <= 0 ? 0.2 : jitterRatio;
        inventoryHoldWindow = inventoryHoldWindow == null ? Duration.ofMinutes(15) : inventoryHoldWindow;
        autoReversalDeadline = autoReversalDeadline == null ? Duration.ofHours(2) : autoReversalDeadline;
    }

    /** The delay before attempt {@code attemptNumber} (1-based), or empty once exhausted. */
    public java.util.Optional<Duration> delayForAttempt(int attemptNumber) {
        int index = attemptNumber - 1;
        return index >= 0 && index < intervals.size()
                ? java.util.Optional.of(intervals.get(index))
                : java.util.Optional.empty();
    }

    public int maxAttempts() {
        return intervals.size();
    }
}
