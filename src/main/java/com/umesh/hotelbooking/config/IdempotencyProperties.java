package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Idempotency-record retention (design doc 8a). {@code retention} has existed in
 * {@code application.yml} since Phase 4; nothing read it until {@link
 * com.umesh.hotelbooking.service.IdempotencyRecordSweeper} (Phase 9) closed that gap. Nested
 * under {@code payment.idempotency} rather than a new top-level namespace, matching every
 * other sweeper's config shape ({@code booking.sweeper}, {@code payment.reconciliation}).
 *
 * @param retention how long a completed record is kept before eviction; must exceed the
 *     longest plausible client retry window
 * @param sweeper scheduling of the eviction sweep
 */
@ConfigurationProperties("payment.idempotency")
public record IdempotencyProperties(Duration retention, Sweeper sweeper) {

    public IdempotencyProperties {
        retention = retention == null ? Duration.ofHours(24) : retention;
        sweeper = sweeper == null ? new Sweeper(true, Duration.ofHours(1)) : sweeper;
    }

    public record Sweeper(boolean enabled, Duration fixedDelay) {

        public Sweeper {
            fixedDelay = fixedDelay == null ? Duration.ofHours(1) : fixedDelay;
        }
    }
}
