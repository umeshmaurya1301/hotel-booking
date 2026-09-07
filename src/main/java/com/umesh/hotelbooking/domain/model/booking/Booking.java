package com.umesh.hotelbooking.domain.model.booking;

import com.umesh.hotelbooking.domain.vo.BookingId;
import com.umesh.hotelbooking.domain.vo.DateRange;
import com.umesh.hotelbooking.domain.vo.GuestCount;
import com.umesh.hotelbooking.domain.vo.GuestId;
import com.umesh.hotelbooking.domain.vo.Money;
import com.umesh.hotelbooking.domain.vo.PropertyId;
import com.umesh.hotelbooking.domain.vo.RoomTypeId;
import com.umesh.hotelbooking.domain.vo.UnitCount;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A reservation of {@code units} rooms of one room type across one date range, priced
 * per night via its {@link BookingLineItem}s.
 *
 * <p>{@code guestId} is a reference only. This class must never hold a guest's name, email,
 * phone or address: a later phase must be able to erase a guest's personal data on request
 * while leaving the booking and its financial trail intact, which would be impossible if
 * personal data lived here.
 *
 * <p>All fields are immutable except {@code state} and {@code version}. State changes only
 * ever happen through {@link #transitionTo(BookingState, BookingStateMachine)}, which
 * delegates the legality check to the state machine rather than encoding transition rules
 * here.
 */
public final class Booking {

    private final BookingId id;
    private final GuestId guestId;
    private final PropertyId propertyId;
    private final RoomTypeId roomTypeId;
    private final DateRange dateRange;
    private final UnitCount units;
    private final GuestCount guestCount;
    private final List<BookingLineItem> lineItems;
    private final Money totalAmount;
    private final Instant createdAt;
    private final Instant holdExpiresAt;
    private BookingState state;
    private long version;

    private Booking(Builder builder) {
        this.id = requireField(builder.id, "id");
        this.guestId = requireField(builder.guestId, "guestId");
        this.propertyId = requireField(builder.propertyId, "propertyId");
        this.roomTypeId = requireField(builder.roomTypeId, "roomTypeId");
        this.dateRange = requireField(builder.dateRange, "dateRange");
        this.units = requireField(builder.units, "units");
        this.guestCount = requireField(builder.guestCount, "guestCount");
        this.createdAt = requireField(builder.createdAt, "createdAt");
        this.holdExpiresAt = requireField(builder.holdExpiresAt, "holdExpiresAt");
        this.state = requireField(builder.state, "state");
        this.version = builder.version;

        List<BookingLineItem> items = requireField(builder.lineItems, "lineItems");
        validateLineItems(items);
        this.lineItems = List.copyOf(items);

        Money computedTotal = sumLineTotals(this.lineItems);
        Money declaredTotal = requireField(builder.totalAmount, "totalAmount");
        if (!computedTotal.equals(declaredTotal)) {
            throw new IllegalArgumentException(
                    "totalAmount " + declaredTotal + " does not equal sum of line totals (" + computedTotal + ")");
        }
        this.totalAmount = declaredTotal;
    }

    private void validateLineItems(List<BookingLineItem> items) {
        List<LocalDate> expectedNights = dateRange.nights();
        if (items.size() != expectedNights.size()) {
            throw new IllegalArgumentException(
                    "expected " + expectedNights.size() + " line items, one per night, but got " + items.size());
        }
        List<LocalDate> actualNights = new ArrayList<>(items.size());
        for (BookingLineItem item : items) {
            if (!item.units().equals(units)) {
                throw new IllegalArgumentException(
                        "line item units " + item.units() + " does not match booking units " + units);
            }
            actualNights.add(item.stayDate());
        }
        List<LocalDate> sortedActual = new ArrayList<>(actualNights);
        sortedActual.sort(null);
        if (!sortedActual.equals(expectedNights)) {
            throw new IllegalArgumentException(
                    "line items must cover exactly dateRange.nights() with no duplicates or gaps");
        }
    }

    private static Money sumLineTotals(List<BookingLineItem> items) {
        Money total = items.get(0).lineTotal();
        for (int i = 1; i < items.size(); i++) {
            total = total.add(items.get(i).lineTotal());
        }
        return total;
    }

    private static <T> T requireField(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    /**
     * Applies {@code next} after delegating legality to {@code fsm}. No transition logic
     * lives in this class — the state machine is the single source of truth.
     */
    public void transitionTo(BookingState next, BookingStateMachine fsm) {
        fsm.assertCanTransition(this.state, next);
        this.state = next;
    }

    /** {@code now} is a parameter, never {@code Instant.now()} — domain code stays deterministic. */
    public boolean isHoldExpired(Instant now) {
        return !now.isBefore(holdExpiresAt);
    }

    /** {@code propertyLocalToday} is a parameter, never {@code LocalDate.now()}. */
    public boolean isPastCheckout(LocalDate propertyLocalToday) {
        return dateRange.isBefore(propertyLocalToday);
    }

    public BookingId getId() {
        return id;
    }

    public GuestId getGuestId() {
        return guestId;
    }

    public PropertyId getPropertyId() {
        return propertyId;
    }

    public RoomTypeId getRoomTypeId() {
        return roomTypeId;
    }

    public DateRange getDateRange() {
        return dateRange;
    }

    public UnitCount getUnits() {
        return units;
    }

    public GuestCount getGuestCount() {
        return guestCount;
    }

    /** Unmodifiable; mutation attempts throw {@link UnsupportedOperationException}. */
    public List<BookingLineItem> getLineItems() {
        return lineItems;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public BookingState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Booking other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Booking{id=" + id + ", propertyId=" + propertyId + ", roomTypeId=" + roomTypeId
                + ", dateRange=" + dateRange + ", units=" + units + ", state=" + state + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Hand-written builder — {@code Booking} has too many fields for a readable constructor. */
    public static final class Builder {
        private BookingId id;
        private GuestId guestId;
        private PropertyId propertyId;
        private RoomTypeId roomTypeId;
        private DateRange dateRange;
        private UnitCount units;
        private GuestCount guestCount;
        private List<BookingLineItem> lineItems;
        private Money totalAmount;
        private Instant createdAt;
        private Instant holdExpiresAt;
        private BookingState state = BookingState.CREATED;
        private long version;

        public Builder id(BookingId id) {
            this.id = id;
            return this;
        }

        public Builder guestId(GuestId guestId) {
            this.guestId = guestId;
            return this;
        }

        public Builder propertyId(PropertyId propertyId) {
            this.propertyId = propertyId;
            return this;
        }

        public Builder roomTypeId(RoomTypeId roomTypeId) {
            this.roomTypeId = roomTypeId;
            return this;
        }

        public Builder dateRange(DateRange dateRange) {
            this.dateRange = dateRange;
            return this;
        }

        public Builder units(UnitCount units) {
            this.units = units;
            return this;
        }

        public Builder guestCount(GuestCount guestCount) {
            this.guestCount = guestCount;
            return this;
        }

        public Builder lineItems(List<BookingLineItem> lineItems) {
            this.lineItems = lineItems;
            return this;
        }

        public Builder totalAmount(Money totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder holdExpiresAt(Instant holdExpiresAt) {
            this.holdExpiresAt = holdExpiresAt;
            return this;
        }

        public Builder state(BookingState state) {
            this.state = state;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Booking build() {
            return new Booking(this);
        }
    }
}
