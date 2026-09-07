package com.umesh.hotelbooking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock tests can move.
 *
 * <p>Hold expiry is a time-dependent behaviour, and the alternatives to controlling time are
 * both bad: sleeping makes the suite slow and flaky, and shrinking the TTL to milliseconds
 * makes the assertion depend on how fast the machine happens to be. Advancing an injected
 * clock tests the real condition deterministically.
 *
 * <p>The current instant lives in a holder shared with every clock produced by
 * {@link #withZone}, so a clock a service derived for a property's timezone still moves when
 * the test advances the original. A snapshot would silently freeze those callers.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this(new AtomicReference<>(instant), zone);
    }

    private MutableClock(AtomicReference<Instant> shared, ZoneId zone) {
        this.now = shared;
        this.zone = zone;
    }

    public void advance(Duration amount) {
        now.updateAndGet(current -> current.plus(amount));
    }

    public void setTo(Instant instant) {
        now.set(instant);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new MutableClock(now, otherZone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
