package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.IdempotencyProperties;
import com.umesh.hotelbooking.entity.IdempotencyRecord;
import com.umesh.hotelbooking.entity.IdempotencyStatus;
import com.umesh.hotelbooking.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes the gap task spec §6 names explicitly: {@code payment.idempotency.retention} had been
 * configured since Phase 4 and read by nothing. Records older than the retention window must
 * be evicted; records inside it must survive - a test that only checked one direction could
 * pass against a sweeper that deletes everything, or one that deletes nothing.
 */
@DataJpaTest
class IdempotencyRecordSweeperTest {

    private static final Instant START = Instant.parse("2026-09-08T00:00:00Z");

    @Autowired
    private IdempotencyRecordRepository repository;

    private IdempotencyRecord recordAged(String msgId, Instant createdAt) {
        return IdempotencyRecord.builder()
                .msgId(msgId).requestHash("hash-" + msgId).status(IdempotencyStatus.COMPLETED)
                .createdAt(createdAt).build();
    }

    @Test
    void recordsOlderThanRetentionAreRemovedWhileNewerOnesSurvive() {
        MutableClock clock = new MutableClock(START, ZoneOffset.UTC);
        IdempotencyProperties properties = new IdempotencyProperties(
                Duration.ofHours(24), new IdempotencyProperties.Sweeper(true, Duration.ofHours(1)));
        IdempotencyRecordSweeper sweeper = new IdempotencyRecordSweeper(repository, properties, clock);

        repository.save(recordAged("old-1", START.minus(Duration.ofHours(25))));
        repository.save(recordAged("old-2", START.minus(Duration.ofHours(48))));
        repository.save(recordAged("boundary", START.minus(Duration.ofHours(24))));
        repository.save(recordAged("fresh-1", START.minus(Duration.ofHours(23))));
        repository.save(recordAged("fresh-2", START));

        int deleted = sweeper.sweep();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAllById(java.util.List.of("old-1", "old-2"))).isEmpty();
        assertThat(repository.findAllById(java.util.List.of("boundary", "fresh-1", "fresh-2")))
                .as("records inside the retention window must survive").hasSize(3);
    }

    @Test
    void anEmptySweepDeletesNothingAndDoesNotThrow() {
        MutableClock clock = new MutableClock(START, ZoneOffset.UTC);
        IdempotencyProperties properties = new IdempotencyProperties(Duration.ofHours(24), null);
        IdempotencyRecordSweeper sweeper = new IdempotencyRecordSweeper(repository, properties, clock);

        repository.save(recordAged("fresh", START));

        assertThat(sweeper.sweep()).isZero();
        assertThat(repository.findById("fresh")).isPresent();
    }
}
