package com.umesh.hotelbooking.infrastructure;

import com.umesh.hotelbooking.domain.port.PropertyClock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Production {@link PropertyClock} wrapping an injected {@link Clock}. The wrapped clock,
 * rather than a direct call to {@code Clock.systemUTC()}, is what keeps this class testable:
 * a fixed {@link Clock} can be substituted without touching call sites. No Spring wiring in
 * this phase — a later phase's configuration provides the {@link Clock} bean.
 */
public final class SystemPropertyClock implements PropertyClock {

    private final Clock clock;

    public SystemPropertyClock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    @Override
    public LocalDate todayAt(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
