package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    List<IdempotencyRecord> findByCreatedAtBefore(Instant cutoff);
}
