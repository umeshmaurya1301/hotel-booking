package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per reconciliation attempt against a stuck payment (design doc 7.6.5).
 *
 * <p>Append-only, like the ledger: never updated, never deleted. The full poll history for a
 * disputed transaction is reconstructible from this table, which is what an escalation to a
 * payment partner actually requires.
 *
 * <p>{@code responseBody} is a short status summary here, not the gateway's raw response —
 * the redaction pipeline that would make persisting a raw payload safe is design doc 12.6,
 * built in a later phase. Storing a real raw body before that exists would be the leak this
 * design goes out of its way to prevent elsewhere.
 */
@Entity
@Table(name = "payment_status_checks",
        indexes = @Index(name = "idx_status_check_due", columnList = "gateway_status, checked_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_status", nullable = false, length = 20)
    private GatewayCheckStatus gatewayStatus;

    @Column(name = "response_summary", length = 500)
    private String responseSummary;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
