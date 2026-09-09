package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.IdempotencyProperties;
import com.umesh.hotelbooking.repository.IdempotencyRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Evicts idempotency records older than {@code payment.idempotency.retention} (design doc 8a).
 * {@link IdempotencyRecordStore} previously exposed only a finder for this — sat unused
 * since Phase 4, since nothing ever called it — and unbounded growth on a dedupe table is
 * exactly the "real production problem" that record's own retention field was always meant to
 * guard against (see PROJECT_STRUCTURE.txt.txt 16.8). That dead finder is gone, replaced by
 * {@link IdempotencyRecordStore#deleteByCreatedAtBefore}, a genuine bulk delete.
 *
 * <p>Separate from {@link IdempotencyRecordSweeperScheduler}, matching {@code BookingSweeper}
 * and {@code BookingSweeperScheduler}'s own split: a test can invoke {@link #sweep()} directly
 * rather than racing a background timer, and the admin/ops surface for triggering an
 * out-of-band sweep (if one is ever added) has somewhere to call into that does not depend on
 * {@code @Scheduled} being active.
 */
@Component
public class IdempotencyRecordSweeper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordSweeper.class);

    private final IdempotencyRecordStore store;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyRecordSweeper(IdempotencyRecordStore store, IdempotencyProperties properties,
                                    Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return how many records were evicted, so a caller (or a test) has something to assert on */
    @Transactional
    public int sweep() {
        Instant cutoff = Instant.now(clock).minus(properties.retention());
        int deleted = store.deleteByCreatedAtBefore(cutoff);
        // An eviction job that runs silently is one nobody notices has stopped.
        if (deleted > 0) {
            log.info("Idempotency record sweep: deleted {} record(s) older than {}", deleted, cutoff);
        }
        return deleted;
    }
}
