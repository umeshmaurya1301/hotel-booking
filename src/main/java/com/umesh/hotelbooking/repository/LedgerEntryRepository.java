package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Append-only: deliberately no update/delete beyond what {@code JpaRepository} exposes. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByLedgerEntryUid(String ledgerEntryUid);
}
