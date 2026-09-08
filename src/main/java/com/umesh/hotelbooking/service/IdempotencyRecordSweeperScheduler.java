package com.umesh.hotelbooking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link IdempotencyRecordSweeper} on a fixed schedule, following {@code
 * BookingSweeperScheduler}'s shape exactly - kept separate from the sweeper itself for the
 * same reason: so the sweep can be invoked directly by a test without racing a background
 * timer.
 */
@Component
@ConditionalOnProperty(name = "payment.idempotency.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyRecordSweeperScheduler {

    private final IdempotencyRecordSweeper sweeper;

    public IdempotencyRecordSweeperScheduler(IdempotencyRecordSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Scheduled(fixedDelayString = "${payment.idempotency.sweeper.fixed-delay:1h}",
            initialDelayString = "${payment.idempotency.sweeper.fixed-delay:1h}")
    public void runScheduledSweep() {
        sweeper.sweep();
    }
}
