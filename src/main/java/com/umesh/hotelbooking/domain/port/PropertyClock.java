package com.umesh.hotelbooking.domain.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Resolves "what is today" and "what time is it now" for domain code, so that stay-date and
 * hold-expiry logic stay deterministic in tests and are never silently wrong for a property
 * in a different timezone than the server.
 *
 * <p>A UTC server calling {@code LocalDate.now()} near midnight returns the wrong calendar
 * day for a property in, say, {@code Asia/Kolkata}. Every "what is today" question in the
 * domain must be answered relative to the property's own zone, via {@link #todayAt(ZoneId)}.
 */
public interface PropertyClock {

    LocalDate todayAt(ZoneId zone);

    Instant now();
}
