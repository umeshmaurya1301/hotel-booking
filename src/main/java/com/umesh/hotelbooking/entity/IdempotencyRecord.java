package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.web.ApiType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency layer (a) of design doc 8: client → API. {@code msgId} is the client's
 * dedup handle, supplied in the request rather than a separate {@code Idempotency-Key}
 * header — two identifiers for one concept is a design smell.
 *
 * <p>{@code msgId} is the primary key rather than a generated one: the UNIQUE-by-being-a-key
 * constraint is what serialises two genuinely concurrent requests carrying the same id — the
 * database constraint does the work, not application locking.
 *
 * <p>Retention (evicting rows after {@code payment.idempotency.retention}) is a stated
 * assumption, not an omission: unbounded growth on a dedupe table is a real production
 * problem, and the window must exceed the longest plausible client retry window, which is
 * why it is hours, not minutes.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @Column(name = "msg_id", length = 100)
    private String msgId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Server-derived operation discriminator (design doc 11.3), stamped for audit. */
    @Enumerated(EnumType.STRING)
    @Column(name = "api_type", length = 40)
    private ApiType apiType;

    /** The request's server-generated trace handle (design doc 9.6: threads through every
     * audit record, this one included). */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
