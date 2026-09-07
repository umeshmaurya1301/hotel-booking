package com.umesh.hotelbooking.domain.model.booking;

import com.umesh.hotelbooking.domain.vo.BookingId;
import com.umesh.hotelbooking.domain.vo.DateRange;
import com.umesh.hotelbooking.domain.vo.GuestCount;
import com.umesh.hotelbooking.domain.vo.GuestId;
import com.umesh.hotelbooking.domain.vo.Money;
import com.umesh.hotelbooking.domain.vo.PropertyId;
import com.umesh.hotelbooking.domain.vo.RoomTypeId;
import com.umesh.hotelbooking.domain.vo.UnitCount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final DateRange THREE_NIGHTS =
            new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

    private Booking.Builder validBuilder(DateRange dateRange, UnitCount units, List<BookingLineItem> lineItems) {
        Money total = lineItems.get(0).lineTotal();
        for (int i = 1; i < lineItems.size(); i++) {
            total = total.add(lineItems.get(i).lineTotal());
        }
        return Booking.builder()
                .id(BookingId.newId())
                .guestId(GuestId.newId())
                .propertyId(PropertyId.newId())
                .roomTypeId(RoomTypeId.newId())
                .dateRange(dateRange)
                .units(units)
                .guestCount(new GuestCount(2, 0))
                .lineItems(lineItems)
                .totalAmount(total)
                .createdAt(Instant.parse("2026-09-01T00:00:00Z"))
                .holdExpiresAt(Instant.parse("2026-09-01T00:15:00Z"))
                .state(BookingState.CREATED);
    }

    private BookingLineItem flatLineItem(LocalDate date, UnitCount units, String pricePerUnit) {
        Money price = Money.inr(pricePerUnit);
        return new BookingLineItem(date, units, price, price.multiply(units.value()));
    }

    @Test
    void lineItemsExactlyMatchingNightsBuildsSuccessfully() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));

        Booking booking = validBuilder(THREE_NIGHTS, units, items).build();

        assertThat(booking.getLineItems()).hasSize(3);
        assertThat(booking.getTotalAmount()).isEqualTo(Money.inr("24000.00"));
    }

    @Test
    void missingNightThrows() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"));

        assertThatThrownBy(() -> validBuilder(THREE_NIGHTS, units, items).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateNightThrows() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));

        assertThatThrownBy(() -> validBuilder(THREE_NIGHTS, units, items).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalAmountMismatchingLineItemSumThrows() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));

        Booking.Builder builder = validBuilder(THREE_NIGHTS, units, items)
                .totalAmount(Money.inr("1.00"));

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void perNightPricesDifferingAcrossAWeekendProduceTheCorrectTotal() {
        // 10th (Thu) and 11th (Fri, surge) and 12th (Sat, surge) - just distinct prices per night.
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "10000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "12000.00"));

        Booking booking = validBuilder(THREE_NIGHTS, units, items).build();

        assertThat(booking.getTotalAmount()).isEqualTo(Money.inr("30000.00"));
    }

    @Test
    void getLineItemsIsUnmodifiable() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));

        Booking booking = validBuilder(THREE_NIGHTS, units, items).build();

        assertThatThrownBy(() -> booking.getLineItems().add(items.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isHoldExpiredBehavesAtBeforeAndAfterTheBoundary() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));
        Instant holdExpiresAt = Instant.parse("2026-09-01T00:15:00Z");

        Booking booking = validBuilder(THREE_NIGHTS, units, items)
                .holdExpiresAt(holdExpiresAt)
                .build();

        assertThat(booking.isHoldExpired(holdExpiresAt.minusSeconds(1))).isFalse();
        assertThat(booking.isHoldExpired(holdExpiresAt)).isTrue();
        assertThat(booking.isHoldExpired(holdExpiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    void twoUnitsAcrossThreeNightsProducesThreeLineItemsEachWithMatchingUnitsAndCorrectTotal() {
        UnitCount twoUnits = UnitCount.of(2);
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), twoUnits, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), twoUnits, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), twoUnits, "8000.00"));

        Booking booking = validBuilder(THREE_NIGHTS, twoUnits, items).build();

        assertThat(booking.getLineItems()).hasSize(3);
        assertThat(booking.getLineItems()).allSatisfy(item -> assertThat(item.units()).isEqualTo(twoUnits));
        assertThat(booking.getTotalAmount()).isEqualTo(Money.inr("8000.00").multiply(2).multiply(3));
    }

    @Test
    void lineItemWithMismatchedUnitsThrows() {
        UnitCount bookingUnits = UnitCount.of(2);
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), bookingUnits, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), UnitCount.one(), "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), bookingUnits, "8000.00"));

        assertThatThrownBy(() -> validBuilder(THREE_NIGHTS, bookingUnits, items).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitionToDelegatesToStateMachine() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));
        Booking booking = validBuilder(THREE_NIGHTS, units, items).build();
        BookingStateMachine fsm = new BookingStateMachine();

        booking.transitionTo(BookingState.PENDING_PAYMENT, fsm);

        assertThat(booking.getState()).isEqualTo(BookingState.PENDING_PAYMENT);
        assertThatThrownBy(() -> booking.transitionTo(BookingState.COMPLETED, fsm))
                .isInstanceOf(com.umesh.hotelbooking.domain.exception.InvalidStateTransitionException.class);
    }

    @Test
    void isPastCheckoutDelegatesToDateRange() {
        UnitCount units = UnitCount.one();
        List<BookingLineItem> items = List.of(
                flatLineItem(LocalDate.of(2026, 9, 10), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 11), units, "8000.00"),
                flatLineItem(LocalDate.of(2026, 9, 12), units, "8000.00"));
        Booking booking = validBuilder(THREE_NIGHTS, units, items).build();

        assertThat(booking.isPastCheckout(LocalDate.of(2026, 9, 12))).isFalse();
        assertThat(booking.isPastCheckout(LocalDate.of(2026, 9, 13))).isTrue();
    }
}
