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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payment attempt against a booking.
 *
 * <p>{@code providerReference} is generated once and reused on every retry — idempotency
 * layer (b) of design doc 8: a fresh reference on retry is the standard double-charge bug.
 * It is what {@code initiate} is called with and what {@code status} polls against, so the
 * gateway sees one transaction no matter how many times our side calls it.
 *
 * <p>{@code inventoryReleased} exists because "is this payment's room-night hold still live"
 * cannot be answered by re-reading {@code daily_inventory} — {@code booked_units} is an
 * aggregate across every booking on that night, not this payment's alone. It is set once,
 * either by the T+15m hold-window release (7.6.1) or by a terminal FAILED outcome, and is
 * what the reconciliation loop's {@code inventoryStillHeld} check reads (7.6.4).
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_provider_reference", columnNames = "provider_reference"),
        indexes = @Index(name = "idx_payment_due", columnList = "state, next_attempt_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String paymentUid;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "bank_code", length = 40)
    private String bankCode;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    /** Stable across every retry of this attempt. Never regenerated. */
    @Column(name = "provider_reference", nullable = false, updatable = false, length = 64)
    private String providerReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentState state;

    /** When this attempt entered UNKNOWN. Null otherwise. Anchors the ladder and the deadline. */
    @Column(name = "unknown_since")
    private Instant unknownSince;

    /** Status-check attempts consumed. Only a genuine PENDING answer increments this (7.6.4). */
    @Builder.Default
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /**
     * Whether this attempt's room-nights have already been released back to sale. Read by
     * the reconciliation loop's {@code inventoryStillHeld} check; written by the hold-window
     * release and by a terminal FAILED outcome.
     */
    @Builder.Default
    @Column(name = "inventory_released", nullable = false)
    private boolean inventoryReleased = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    private void onCreate() {
        if (paymentUid == null) {
            paymentUid = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
        if (state == null) {
            state = PaymentState.INITIATED;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public void transitionTo(PaymentState next) {
        PaymentStateMachine.assertCanTransition(this.state, next);
        this.state = next;
    }

    /** Moves to UNKNOWN and starts the status-check clock, scheduling the first attempt. */
    public void markUnknown(Instant now, java.time.Duration firstDelay) {
        transitionTo(PaymentState.UNKNOWN);
        this.unknownSince = now;
        this.attemptNo = 0;
        this.nextAttemptAt = now.plus(firstDelay);
    }

    public boolean isDueForCheck(Instant now) {
        return state == PaymentState.UNKNOWN && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    public boolean hasExceededDeadline(Instant now, java.time.Duration deadline) {
        return unknownSince != null && now.isAfter(unknownSince.plus(deadline));
    }

    public boolean isDueForInventoryRelease(Instant now, java.time.Duration holdWindow) {
        return !inventoryReleased && unknownSince != null && now.isAfter(unknownSince.plus(holdWindow));
    }
}
