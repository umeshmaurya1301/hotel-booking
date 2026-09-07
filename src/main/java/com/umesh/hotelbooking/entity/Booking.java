package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidDateRangeException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reservation of {@code units} rooms of one room type across one date range.
 *
 * <p>{@code id} is the database primary key and never leaves this application; {@code
 * bookingUid} is the UUID every API, log line and lookup actually uses. {@code guestId} is a
 * reference only — no guest name, email, phone or address is ever stored here, so a guest's
 * personal data can be erased later without touching booking or financial history.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    /** A domain rule, not a database constraint: kept here because Bean Validation can't express it. */
    public static final int MAX_STAY_NIGHTS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String bookingUid;

    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @NotNull
    @Column(nullable = false)
    private LocalDate checkIn;

    @NotNull
    @Column(nullable = false)
    private LocalDate checkOut;

    @Min(1)
    @Max(10)
    @Column(nullable = false)
    private int units;

    @Min(1)
    @Column(nullable = false)
    private int adults;

    @Min(0)
    @Column(nullable = false)
    private int children;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant holdExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingState state;

    /** JPA-managed optimistic lock; Hibernate increments and checks this automatically. */
    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingLineItem> lineItems = new ArrayList<>();

    @PrePersist
    private void onCreate() {
        if (bookingUid == null) {
            bookingUid = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (state == null) {
            state = BookingState.CREATED;
        }
        validateDateRange();
    }

    @PreUpdate
    private void onUpdate() {
        validateDateRange();
    }

    /**
     * Checkout-exclusive nights: a hotel night is the night a guest sleeps there, and
     * checkout day is not one. Enforced here, not left to the caller, so it can't be
     * bypassed by constructing a Booking directly via the builder.
     *
     * <p>Public, not just a lifecycle callback, so the booking service can reject a bad range
     * up front — before any inventory is reserved — rather than discovering it at flush time
     * with room-nights already held.
     */
    public void validateDateRange() {
        if (checkIn == null || checkOut == null) {
            throw new InvalidDateRangeException("checkIn and checkOut must not be null");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidDateRangeException(
                    "checkOut (" + checkOut + ") must be after checkIn (" + checkIn + ")");
        }
        long spanNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (spanNights > MAX_STAY_NIGHTS) {
            throw new InvalidDateRangeException(
                    "stay of " + spanNights + " nights exceeds the maximum of " + MAX_STAY_NIGHTS);
        }
    }

    /** Every calendar date from checkIn inclusive to checkOut exclusive, ascending. */
    public List<LocalDate> nights() {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            result.add(date);
        }
        return result;
    }

    public int nightCount() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** Keeps both sides of the bidirectional association in sync. */
    public void addLineItem(BookingLineItem item) {
        lineItems.add(item);
        item.setBooking(this);
    }

    public void transitionTo(BookingState next) {
        BookingStateMachine.assertCanTransition(this.state, next);
        this.state = next;
    }

    public boolean isHoldExpired(Instant now) {
        return !now.isBefore(holdExpiresAt);
    }

    public boolean isPastCheckout(LocalDate propertyLocalToday) {
        return !checkOut.isAfter(propertyLocalToday);
    }
}
