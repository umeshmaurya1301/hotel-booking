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
 * Undoes a settled payment because the transaction should not have stood — never because the
 * guest asked for money back (design doc 9.1). Always full, never policy-applied: those two
 * properties are exactly what distinguish it from {@link Refund}.
 */
@Entity
@Table(name = "reversals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reversal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reversal_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String reversalUid;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReversalReason reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReversalState state;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Version
    private Long version;

    @PrePersist
    private void onCreate() {
        if (reversalUid == null) {
            reversalUid = UUID.randomUUID().toString();
        }
        if (state == null) {
            state = ReversalState.INITIATED;
        }
    }

    public void transitionTo(ReversalState next) {
        ReversalStateMachine.assertCanTransition(this.state, next);
        this.state = next;
    }
}
