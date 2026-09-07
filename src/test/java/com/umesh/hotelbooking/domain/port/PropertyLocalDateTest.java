package com.umesh.hotelbooking.domain.port;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyLocalDateTest {

    @Test
    void todayAtIstIsAlreadyTheNextDayWhenUtcIsStillOnThePreviousDay() {
        // 2026-09-07T18:40:00Z is 2026-09-08T00:10:00+05:30 in Asia/Kolkata (UTC+5:30) -
        // already past midnight locally while UTC is still on the 7th. A server that answers
        // "what day is it" using LocalDate.now() in the JVM's default (UTC) zone would get
        // this wrong for the property.
        Instant fixedInstant = Instant.parse("2026-09-07T18:40:00Z");
        PropertyClock clock = new FixedPropertyClock(fixedInstant);

        LocalDate propertyLocalToday = clock.todayAt(ZoneId.of("Asia/Kolkata"));
        LocalDate utcToday = LocalDate.ofInstant(fixedInstant, ZoneOffset.UTC);

        assertThat(propertyLocalToday).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(utcToday).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(propertyLocalToday).isNotEqualTo(utcToday);
    }

    @Test
    void nowReturnsTheFixedInstantUnchanged() {
        Instant fixedInstant = Instant.parse("2026-09-07T18:40:00Z");
        PropertyClock clock = new FixedPropertyClock(fixedInstant);

        assertThat(clock.now()).isEqualTo(fixedInstant);
    }
}
