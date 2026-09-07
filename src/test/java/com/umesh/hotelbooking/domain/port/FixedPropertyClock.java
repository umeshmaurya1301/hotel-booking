package com.umesh.hotelbooking.domain.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Test-only {@link PropertyClock} pinned to a fixed instant, so property-local date and hold
 * expiry assertions are deterministic regardless of when or where the test runs.
 */
public final class FixedPropertyClock implements PropertyClock {

    private final Instant fixedInstant;

    public FixedPropertyClock(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
    }

    @Override
    public LocalDate todayAt(ZoneId zone) {
        return LocalDate.ofInstant(fixedInstant, zone);
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }
}
