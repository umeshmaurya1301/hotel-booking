package com.umesh.hotelbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * The envelope every state-changing {@code /api/v1/**} request arrives in (design doc 11.1).
 *
 * <p>{@code msgId} is the single idempotency key (design doc 8a) — there is deliberately no
 * separate {@code Idempotency-Key} header, since two identifiers for one concept is a design
 * smell. It is required by construction, which is what makes booking creation idempotent
 * along with everything else once every state-changing endpoint takes this envelope.
 *
 * <p>{@code @Valid} on {@link #payload()} is what makes the inner DTO's own Bean Validation
 * constraints fire. Without it, a {@code @NotBlank} field inside e.g. {@code
 * CreateBookingRequest} is silently ignored by the validator.
 *
 * <p>{@code channel} and {@code version} are validated only as non-blank free text, not as an
 * enum: they are client-declared labels recorded for audit, and rejecting an unrecognised
 * channel would be a deployment-coupling problem (every new caller needs a server change
 * first) rather than a safety feature.
 *
 * <p>{@code timestamp} is the client's own clock, recorded and nothing else in this phase. A
 * replay-window check belongs to the webhook path (design doc 12.3, Phase 7), where the
 * sender is a machine with a shared secret and a tight clock. Applying a similar window to a
 * guest's phone — which may have a wrong clock, or a slow network — would reject legitimate
 * traffic for no safety gained.
 *
 * @param msgId client-supplied idempotency + dedup key — a UUID by convention, not enforced
 * @param timestamp the client's clock at the time of the request; recorded, not validated
 * @param channel free-form caller label, e.g. {@code WEB}, {@code MOBILE}, {@code PARTNER}
 * @param version payload contract version the client believes it is speaking
 * @param initiatorId optional caller identity, distinct from {@code msgId}
 * @param payload the operation-specific request body
 */
public record ApiRequest<T>(
        @NotBlank String msgId,
        @NotNull Instant timestamp,
        @NotBlank String channel,
        @NotBlank String version,
        String initiatorId,
        @NotNull @Valid T payload) {
}
