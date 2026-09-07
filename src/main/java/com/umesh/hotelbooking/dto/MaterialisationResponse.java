package com.umesh.hotelbooking.dto;

import java.time.LocalDate;

/**
 * Outcome of a materialisation run. {@code nightsCreated} counts rows actually inserted —
 * re-running over a range that already exists reports zero rather than silently rewriting it.
 */
public record MaterialisationResponse(
        int propertiesTouched,
        int roomTypesTouched,
        int nightsCreated,
        LocalDate horizonEnd) {
}
