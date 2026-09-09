package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.IdempotencyRecord;
import com.umesh.hotelbooking.repository.IdempotencyRecordStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link IdempotencyRecordStore}. */
class JpaIdempotencyRecordStore implements IdempotencyRecordStore {

    private final JpaIdempotencyRecordRepository repository;

    JpaIdempotencyRecordStore(JpaIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return repository.save(record);
    }

    @Override
    public IdempotencyRecord saveAndFlush(IdempotencyRecord record) {
        return repository.saveAndFlush(record);
    }

    @Override
    public Optional<IdempotencyRecord> findById(String msgId) {
        return repository.findById(msgId);
    }

    @Override
    public List<IdempotencyRecord> findAllById(Iterable<String> msgIds) {
        return repository.findAllById(msgIds);
    }

    @Override
    public List<IdempotencyRecord> findAll() {
        return repository.findAll();
    }

    @Override
    public int deleteByCreatedAtBefore(Instant cutoff) {
        return repository.deleteByCreatedAtBefore(cutoff);
    }
}
