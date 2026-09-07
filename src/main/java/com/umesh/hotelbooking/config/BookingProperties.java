package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Booking lifecycle settings (design doc 4.4, 5.3).
 *
 * @param holdTtl how long a created booking holds its room-nights before the sweeper
 *     releases them. Without this, inventory leaks permanently on every abandoned booking.
 * @param sweeper scheduling of the hold-expiry and completion sweep
 * @param retry bounded retry for transient lock contention on the reservation path
 */
@ConfigurationProperties("booking")
public record BookingProperties(Duration holdTtl, Sweeper sweeper, Retry retry) {

    public BookingProperties {
        holdTtl = holdTtl == null ? Duration.ofMinutes(15) : holdTtl;
        sweeper = sweeper == null ? new Sweeper(true, Duration.ofSeconds(60)) : sweeper;
        retry = retry == null ? new Retry(3, Duration.ofMillis(25), Duration.ofMillis(250)) : retry;
    }

    public record Sweeper(boolean enabled, Duration fixedDelay) {
    }

    public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {
    }
}
