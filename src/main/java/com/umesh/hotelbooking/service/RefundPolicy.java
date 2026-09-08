package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Computes what a guest gets back for cancelling a confirmed booking (design doc 9.3).
 *
 * <p>Each implementation is a complete, self-contained cancellation policy — not a tier
 * within a combined scheme. A property group is configured with exactly one policy code
 * (design doc 6.4's per-group resolution, applied to refunds), which is what makes this the
 * brief's "pluggable" requirement rather than a hardcoded rule.
 *
 * <p>{@code propertyZone} exists because "hours until check-in" cannot be answered correctly
 * from a bare {@link Instant} and a property-local {@code LocalDate} alone (design doc 4.5):
 * the check-in date must be resolved to an instant in the property's own zone, not the
 * server's, or a guest near a timezone boundary gets the wrong threshold.
 */
public interface RefundPolicy {

    BigDecimal calculate(Booking booking, ZoneId propertyZone, Instant cancelledAt);

    String policyCode();
}
