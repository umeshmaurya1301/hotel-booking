package com.umesh.hotelbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The single {@link Clock} every service injects when it needs "now" (design doc 4.5).
 *
 * <p>Services must never call {@code Instant.now()} or {@code LocalDate.now()} directly: a
 * UTC server asking for "today" near midnight returns the wrong calendar day for a property
 * in Asia/Kolkata, and a hard-coded clock cannot be pinned in a test. Resolving a property's
 * local date always goes through {@code LocalDate.now(clock.withZone(property.zone()))}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
