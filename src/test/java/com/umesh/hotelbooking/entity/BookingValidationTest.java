package com.umesh.hotelbooking.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code jakarta.validation} constraint annotations on {@link Booking} directly
 * with a {@link Validator}, without any Spring context — these annotations are only enforced
 * automatically once a controller validates an incoming DTO with {@code @Valid} in a later
 * phase, so this is what proves they are wired correctly today.
 */
class BookingValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Booking.BookingBuilder validBuilder() {
        return Booking.builder()
                .guestId(1L)
                .propertyId(2L)
                .roomTypeId(3L)
                .checkIn(LocalDate.of(2026, 9, 10))
                .checkOut(LocalDate.of(2026, 9, 13))
                .units(1)
                .adults(2)
                .children(0)
                .totalAmount(new BigDecimal("24000.00"))
                .currency("INR")
                .createdAt(Instant.parse("2026-09-01T00:00:00Z"))
                .holdExpiresAt(Instant.parse("2026-09-01T00:15:00Z"))
                .state(BookingState.CREATED);
    }

    @Test
    void validBookingHasNoViolations() {
        Set<ConstraintViolation<Booking>> violations = validator.validate(validBuilder().build());

        assertThat(violations).isEmpty();
    }

    @Test
    void zeroUnitsViolatesMinConstraint() {
        Booking booking = validBuilder().units(0).build();

        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("units"));
    }

    @Test
    void unitsAboveMaxViolatesMaxConstraint() {
        Booking booking = validBuilder().units(11).build();

        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("units"));
    }

    @Test
    void zeroAdultsViolatesMinConstraint() {
        Booking booking = validBuilder().adults(0).build();

        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("adults"));
    }

    @Test
    void nullCheckInViolatesNotNullConstraint() {
        Booking booking = validBuilder().checkIn(null).build();

        Set<ConstraintViolation<Booking>> violations = validator.validate(booking);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("checkIn"));
    }
}
