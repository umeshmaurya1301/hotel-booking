package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidDateRangeException;
import com.umesh.hotelbooking.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

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
    void nightsExcludesCheckoutDay() {
        Booking booking = validBuilder().build();

        assertThat(booking.nights()).containsExactly(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12));
        assertThat(booking.nightCount()).isEqualTo(3);
    }

    @Test
    void validateDateRangeRejectsCheckoutNotAfterCheckin() {
        Booking booking = validBuilder().checkOut(LocalDate.of(2026, 9, 10)).build();

        assertThatThrownBy(booking::validateDateRange).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void validateDateRangeRejectsStayExceedingMax() {
        LocalDate checkIn = LocalDate.of(2026, 1, 1);
        Booking booking = validBuilder()
                .checkIn(checkIn)
                .checkOut(checkIn.plusDays(Booking.MAX_STAY_NIGHTS + 1))
                .build();

        assertThatThrownBy(booking::validateDateRange).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void validateDateRangeAcceptsAValidRange() {
        Booking booking = validBuilder().build();

        assertThatCode(booking);
    }

    private void assertThatCode(Booking booking) {
        booking.validateDateRange(); // must not throw
    }

    @Test
    void transitionToDelegatesToStateMachine() {
        Booking booking = validBuilder().build();

        booking.transitionTo(BookingState.PENDING_PAYMENT);

        assertThat(booking.getState()).isEqualTo(BookingState.PENDING_PAYMENT);
        assertThatThrownBy(() -> booking.transitionTo(BookingState.COMPLETED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void isHoldExpiredBehavesAtBeforeAndAfterTheBoundary() {
        Instant holdExpiresAt = Instant.parse("2026-09-01T00:15:00Z");
        Booking booking = validBuilder().holdExpiresAt(holdExpiresAt).build();

        assertThat(booking.isHoldExpired(holdExpiresAt.minusSeconds(1))).isFalse();
        assertThat(booking.isHoldExpired(holdExpiresAt)).isTrue();
        assertThat(booking.isHoldExpired(holdExpiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    void isPastCheckoutDelegatesToDateRange() {
        Booking booking = validBuilder().build(); // checkOut = 2026-09-13

        assertThat(booking.isPastCheckout(LocalDate.of(2026, 9, 12))).isFalse();
        assertThat(booking.isPastCheckout(LocalDate.of(2026, 9, 13))).isTrue();
    }

    @Test
    void addLineItemKeepsBothSidesOfTheAssociationInSync() {
        Booking booking = validBuilder().build();
        BookingLineItem item = BookingLineItem.builder()
                .stayDate(LocalDate.of(2026, 9, 10))
                .units(1)
                .pricePerUnit(new BigDecimal("8000.00"))
                .lineTotal(new BigDecimal("8000.00"))
                .build();

        booking.addLineItem(item);

        assertThat(booking.getLineItems()).containsExactly(item);
        assertThat(item.getBooking()).isSameAs(booking);
    }

    @Test
    void onCreateAssignsBookingUidAndDefaultsWhenAbsent() {
        Booking booking = validBuilder()
                .bookingUid(null)
                .createdAt(null)
                .state(null)
                .build();

        invokeOnCreate(booking);

        assertThat(booking.getBookingUid()).isNotBlank();
        assertThat(booking.getCreatedAt()).isNotNull();
        assertThat(booking.getState()).isEqualTo(BookingState.CREATED);
    }

    private void invokeOnCreate(Booking booking) {
        try {
            var method = Booking.class.getDeclaredMethod("onCreate");
            method.setAccessible(true);
            method.invoke(booking);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
