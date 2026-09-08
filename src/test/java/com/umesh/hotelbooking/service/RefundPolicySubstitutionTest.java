package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 15.1 test 9: the Strategy is genuinely pluggable, not three classes wired to look that way.
 * Every case runs the same booking through the same cancellation instant with a different
 * policy code resolved via {@link RefundPolicyFactory} - never by instantiating a policy
 * directly - so what is actually under test is the substitution, not just that three classes
 * can each compute a number.
 *
 * <p>No Spring context: every {@link RefundPolicy} implementation is a plain, dependency-free
 * {@code @Component}, so the factory is built directly from the three real instances.
 */
class RefundPolicySubstitutionTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final RefundPolicyFactory factory = new RefundPolicyFactory(List.of(
            new FullRefundBefore48Hours(), new FiftyPercentBefore24Hours(), new NoRefundAfterCheckIn()));

    private Booking bookingCheckingInOn(LocalDate checkIn) {
        return Booking.builder().totalAmount(new BigDecimal("1000.00")).checkIn(checkIn)
                .checkOut(checkIn.plusDays(1)).build();
    }

    @Test
    void sameCancellationSameBookingThreeDifferentPoliciesThreeDifferentAmounts() {
        Booking booking = bookingCheckingInOn(LocalDate.of(2026, 9, 20));
        // 72 hours' notice: within every policy's own notice window.
        Instant cancelledAt = LocalDate.of(2026, 9, 17).atStartOfDay(ZONE).toInstant();

        BigDecimal full = factory.resolve(FullRefundBefore48Hours.CODE).calculate(booking, ZONE, cancelledAt);
        BigDecimal half = factory.resolve(FiftyPercentBefore24Hours.CODE).calculate(booking, ZONE, cancelledAt);
        BigDecimal none = factory.resolve(NoRefundAfterCheckIn.CODE).calculate(booking, ZONE, cancelledAt);

        assertThat(full).isEqualByComparingTo("1000.00");
        assertThat(half).isEqualByComparingTo("500.00");
        // NoRefundAfterCheckIn refunds in full any time before check-in - free cancellation.
        assertThat(none).isEqualByComparingTo("1000.00");
    }

    @Test
    void fullRefundPolicyGrantsNothingInsideItsFortyEightHourWindow() {
        Booking booking = bookingCheckingInOn(LocalDate.of(2026, 9, 20));
        Instant cancelledAt = LocalDate.of(2026, 9, 19).atStartOfDay(ZONE).toInstant(); // 24h notice

        BigDecimal amount = factory.resolve(FullRefundBefore48Hours.CODE).calculate(booking, ZONE, cancelledAt);
        assertThat(amount).isEqualByComparingTo("0.00");
    }

    /** design doc 16.4's own flagged boundary: exactly the 24-hour mark. */
    @Test
    void fiftyPercentPolicyAtExactlyTwentyFourHoursStillGrantsTheHalfRefund() {
        Booking booking = bookingCheckingInOn(LocalDate.of(2026, 9, 20));
        Instant checkInInstant = LocalDate.of(2026, 9, 20).atStartOfDay(ZONE).toInstant();
        Instant exactlyOnBoundary = checkInInstant.minus(java.time.Duration.ofHours(24));

        BigDecimal amount = factory.resolve(FiftyPercentBefore24Hours.CODE).calculate(booking, ZONE, exactlyOnBoundary);
        assertThat(amount)
                .as("the boundary itself is still \"24 hours or more\" notice - not yet inside the window")
                .isEqualByComparingTo("500.00");
    }

    @Test
    void fiftyPercentPolicyOneSecondPastTheTwentyFourHourBoundaryGrantsNothing() {
        Booking booking = bookingCheckingInOn(LocalDate.of(2026, 9, 20));
        Instant checkInInstant = LocalDate.of(2026, 9, 20).atStartOfDay(ZONE).toInstant();
        Instant justInsideTheWindow = checkInInstant.minus(java.time.Duration.ofHours(24)).plusSeconds(1);

        BigDecimal amount = factory.resolve(FiftyPercentBefore24Hours.CODE).calculate(booking, ZONE, justInsideTheWindow);
        assertThat(amount).isEqualByComparingTo("0.00");
    }

    @Test
    void noRefundAfterCheckInPolicyGrantsNothingOnceCheckInHasBegun() {
        Booking booking = bookingCheckingInOn(LocalDate.of(2026, 9, 20));
        Instant afterCheckIn = LocalDate.of(2026, 9, 20).atStartOfDay(ZONE).toInstant().plusSeconds(1);

        BigDecimal amount = factory.resolve(NoRefundAfterCheckIn.CODE).calculate(booking, ZONE, afterCheckIn);
        assertThat(amount).isEqualByComparingTo("0.00");
    }

    @Test
    void resolveOrDefaultFallsBackToTheFullRefundPolicyWhenNoCodeIsConfigured() {
        assertThat(factory.resolveOrDefault(null).policyCode()).isEqualTo(FullRefundBefore48Hours.CODE);
        assertThat(factory.resolveOrDefault("")).isSameAs(factory.resolve(FullRefundBefore48Hours.CODE));
    }
}
