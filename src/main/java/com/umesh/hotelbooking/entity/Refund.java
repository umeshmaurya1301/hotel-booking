package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
 * A guest-initiated refund against a settled payment (design doc 9.1, 9.5) — "money back, per
 * policy", triggered by cancelling a confirmed booking. Distinct from {@link Reversal}, which
 * is always full and never policy-applied.
 */
@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String refundUid;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "policy_code", length = 40)
    private String policyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundState state;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    @PrePersist
    private void onCreate() {
        if (refundUid == null) {
            refundUid = UUID.randomUUID().toString();
        }
        if (state == null) {
            state = RefundState.REQUESTED;
        }
    }

    public void transitionTo(RefundState next) {
        RefundStateMachine.assertCanTransition(this.state, next);
        this.state = next;
    }
}
