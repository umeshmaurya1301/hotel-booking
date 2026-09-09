package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;

/** Spring Data JPA queries behind {@link JpaIdempotencyRecordStore}. Not injected outside this
 * package. */
interface JpaIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /** A single {@code DELETE} statement, which is what the port means by deleting in the store
     * rather than in the application. */
    @Modifying
    int deleteByCreatedAtBefore(Instant cutoff);
}
