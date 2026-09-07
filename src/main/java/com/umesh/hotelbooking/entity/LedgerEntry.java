package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Placeholder for the append-only ledger entry — enough to give {@code
 * LedgerEntryRepository} a concrete target. The full record (booking/payment references,
 * entry type, amount, direction) is built in the ledger phase.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_entry_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String ledgerEntryUid;

    @PrePersist
    private void onCreate() {
        if (ledgerEntryUid == null) {
            ledgerEntryUid = UUID.randomUUID().toString();
        }
    }
}
