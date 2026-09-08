package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line in the financial trail (design doc 9.4). Never updated, never deleted —
 * a correction is a new row, never a change to an old one. Balance is derived by summing
 * these, not stored, which is what lets "why is this number what it is" have an answer.
 *
 * <p>{@code amount} is always positive; direction carries the sign. Over-refunding is
 * structurally prevented by an invariant checked before any REFUND or REVERSAL row is
 * written — {@code sum(REFUND) + sum(REVERSAL) <= sum(CHARGE)} per booking — enforced by
 * {@code LedgerService}, not by a constraint on this table, since it spans multiple rows.
 *
 * <p>Deliberately not double-entry: no chart of accounts, no counterparty, no trial balance.
 * There is no bank statement in this scope to reconcile against, and building one would
 * misrepresent the depth of accounting this system actually needs.
 */
@Entity
@Table(name = "ledger_entries", indexes = @Index(name = "idx_ledger_booking", columnList = "booking_id, occurred_at"))
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

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** Nullable: an ADJUSTMENT may not trace to a specific payment attempt. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @PrePersist
    private void onCreate() {
        if (ledgerEntryUid == null) {
            ledgerEntryUid = UUID.randomUUID().toString();
        }
    }
}
