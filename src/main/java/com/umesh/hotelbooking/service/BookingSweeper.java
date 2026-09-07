package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.SweepResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.event.BookingCompletedEvent;
import com.umesh.hotelbooking.event.BookingExpiredEvent;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Releases lapsed holds and completes finished stays (design doc 4.4).
 *
 * <p>The first job is why this exists: without it, every abandoned booking leaks its
 * room-nights permanently. A guest who creates a booking and never pays would take those
 * nights off sale forever.
 *
 * <p>The second is smaller but not optional. {@code CONFIRMED -> COMPLETED} is in the
 * transition table, and nothing else in the system can reach it. An unreachable state in a
 * state machine is a defect, not an unused feature.
 *
 * <p>Each booking is swept in its own transaction via {@link TransactionTemplate} rather than
 * one transaction for the whole pass: a single poisoned booking must not roll back the work
 * done for every other one, and a long-running sweep must not hold locks across the batch.
 */
@Component
public class BookingSweeper {

    private static final Logger log = LoggerFactory.getLogger(BookingSweeper.class);

    /** States whose hold can lapse. A CONFIRMED booking is paid for and never expires. */
    private static final List<BookingState> EXPIRABLE =
            List.of(BookingState.CREATED, BookingState.PENDING_PAYMENT);

    /**
     * The furthest ahead any timezone can be (UTC+14, Kiritimati). Used to build a superset
     * of completable bookings before filtering each by its property's actual zone.
     */
    private static final ZoneOffset MAX_ZONE_AHEAD = ZoneOffset.ofHours(14);

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final InventoryReservationService reservationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BookingSweeper(BookingRepository bookingRepository,
                          PropertyRepository propertyRepository,
                          RoomTypeRepository roomTypeRepository,
                          InventoryReservationService reservationService,
                          ApplicationEventPublisher eventPublisher,
                          TransactionTemplate transactionTemplate,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.reservationService = reservationService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public SweepResponse sweep() {
        AtomicInteger skipped = new AtomicInteger();
        int expired = expireLapsedHolds(skipped);
        int completed = completePastCheckouts(skipped);

        if (expired > 0 || completed > 0) {
            log.info("Sweep complete: holdsExpired={} bookingsCompleted={} skipped={}",
                    expired, completed, skipped.get());
        }
        return new SweepResponse(expired, completed, skipped.get());
    }

    private int expireLapsedHolds(AtomicInteger skipped) {
        Instant now = clock.instant();
        List<Booking> candidates = bookingRepository.findByStateInAndHoldExpiresAtBefore(EXPIRABLE, now);

        int expired = 0;
        for (Booking candidate : candidates) {
            if (expireOne(candidate.getId(), now, skipped)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * Expires one booking and puts its nights back on sale.
     *
     * <p>The race worth naming: the sweeper can fire while a payment is in flight. State is
     * therefore re-read and re-checked inside this transaction, and the booking's
     * {@code @Version} makes the write fail if a payment committed in between. A payment that
     * settles first wins and the sweep becomes a no-op — which is why a concurrent
     * modification here is counted and moved past, not retried and not logged as an error.
     * A payment that settles <em>after</em> the release lands on the reversal path in a later
     * phase, using this same machinery.
     */
    private boolean expireOne(Long bookingId, Instant now, AtomicInteger skipped) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Optional<Booking> reloaded = bookingRepository.findById(bookingId);
                if (reloaded.isEmpty()) {
                    return false;
                }
                Booking booking = reloaded.get();
                if (!EXPIRABLE.contains(booking.getState()) || !booking.isHoldExpired(now)) {
                    return false;
                }

                booking.transitionTo(BookingState.EXPIRED);
                int released = reservationService.release(
                        booking.getRoomTypeId(), booking.nights(), booking.getUnits());

                String roomTypeUid = roomTypeRepository.findById(booking.getRoomTypeId())
                        .map(RoomType::getRoomTypeUid).orElse(null);
                eventPublisher.publishEvent(new BookingExpiredEvent(
                        booking.getBookingUid(), roomTypeUid, booking.getUnits(), released, now));
                return true;
            }));
        } catch (OptimisticLockingFailureException e) {
            skipped.incrementAndGet();
            return false;
        }
    }

    /**
     * Completes stays whose checkout has passed <em>in the property's own timezone</em>.
     *
     * <p>A single query cannot ask this across properties in different zones, so it takes a
     * superset using the furthest-ahead offset on earth and filters precisely per booking.
     * Comparing against the server's date instead would complete a Bengaluru stay a few hours
     * early or late depending on where the server happens to run.
     */
    private int completePastCheckouts(AtomicInteger skipped) {
        LocalDate widestToday = LocalDate.now(clock.withZone(MAX_ZONE_AHEAD));
        List<Booking> candidates =
                bookingRepository.findByStateAndCheckOutLessThanEqual(BookingState.CONFIRMED, widestToday);

        int completed = 0;
        for (Booking candidate : candidates) {
            if (completeOne(candidate.getId(), skipped)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean completeOne(Long bookingId, AtomicInteger skipped) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                Optional<Booking> reloaded = bookingRepository.findById(bookingId);
                if (reloaded.isEmpty() || reloaded.get().getState() != BookingState.CONFIRMED) {
                    return false;
                }
                Booking booking = reloaded.get();

                Optional<Property> property = propertyRepository.findById(booking.getPropertyId());
                if (property.isEmpty()) {
                    return false;
                }
                LocalDate propertyLocalToday = LocalDate.now(clock.withZone(property.get().zone()));
                if (!booking.isPastCheckout(propertyLocalToday)) {
                    return false;
                }

                booking.transitionTo(BookingState.COMPLETED);
                eventPublisher.publishEvent(
                        new BookingCompletedEvent(booking.getBookingUid(), clock.instant()));
                return true;
            }));
        } catch (OptimisticLockingFailureException e) {
            skipped.incrementAndGet();
            return false;
        }
    }
}
