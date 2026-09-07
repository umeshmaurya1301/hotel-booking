package com.umesh.hotelbooking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link BookingSweeper} on a fixed schedule.
 *
 * <p>Kept separate from the sweeper itself so the sweep can be invoked directly — by the
 * admin endpoint, and by tests that need to control exactly when it runs rather than race a
 * background timer.
 */
@Component
@ConditionalOnProperty(name = "booking.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class BookingSweeperScheduler {

    private final BookingSweeper bookingSweeper;

    public BookingSweeperScheduler(BookingSweeper bookingSweeper) {
        this.bookingSweeper = bookingSweeper;
    }

    @Scheduled(fixedDelayString = "${booking.sweeper.fixed-delay:60s}",
            initialDelayString = "${booking.sweeper.fixed-delay:60s}")
    public void runScheduledSweep() {
        bookingSweeper.sweep();
    }
}
