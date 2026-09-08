package com.umesh.hotelbooking.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Every inbound callback, persisted before it is processed (design doc 12.3 step 6, 9.6).
 * Follows the two-id convention of design doc 3.2 like every other top-level table.
 *
 * <p>Holds <b>no personal data</b> — only the redacted {@code payload} and opaque identifiers
 * (design doc 12.6.3): an append-only audit table is exactly the kind of record that PII must
 * never enter inline.
 *
 * <p>{@code UNIQUE (provider_code, event_id)} is idempotency layer (c) of design doc 8c: the
 * constraint, not an {@code exists} check alone, is what serialises two genuinely concurrent
 * deliveries of the same callback — a routine occurrence with a retrying provider.
 */
@Entity
@Table(name = "webhook_event_log",
        uniqueConstraints = @UniqueConstraint(name = "uq_webhook_event", columnNames = {"provider_code", "event_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_event_log_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String webhookEventLogUid;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", length = 40)
    private String eventType;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookOutcome outcome;

    /** Already redacted by {@code PayloadRedactor} before this entity is ever built — never
     * the raw provider body (design doc 12.6.3, 12.6.4). */
    @Lob
    @Column
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    private void onCreate() {
        if (webhookEventLogUid == null) {
            webhookEventLogUid = UUID.randomUUID().toString();
        }
    }
}
