package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Bulk-deletes every record older than {@code cutoff} in a single {@code DELETE} statement
     * and returns how many rows it removed, so {@code IdempotencyRecordSweeper} has something
     * to log. {@code @Modifying}, not a load-then-delete loop: a dedupe table growing without
     * bound (design doc 8a) is exactly the case where fetching every stale row into memory
     * first, just to delete it, would itself become the problem this method exists to avoid.
     */
    @Modifying
    int deleteByCreatedAtBefore(Instant cutoff);
}
