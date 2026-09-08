package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.web.RequestMeta;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * The booking entry point. Deliberately thin: it exists to put a bounded retry <em>around</em>
 * {@link BookingCreator}'s transaction rather than inside it.
 *
 * <p>Total ordering on {@code (roomTypeId, stayDate)} is what prevents deadlock on the
 * reservation path (design doc 5.3). This retry is the secondary defence for what ordering
 * cannot promise: that no other statement anywhere in the system ever interleaves badly, and
 * that a busy room-night never exhausts the database's lock timeout. It is bounded, backs off
 * exponentially and jitters, because a fixed-interval retry across many threads just
 * reassembles the thundering herd it was meant to break up.
 *
 * <p>Retrying is safe here precisely because each attempt is a whole transaction that either
 * commits or rolls back. A lock timeout means nothing was reserved, so a second attempt
 * cannot double-book. Business outcomes are deliberately <em>not</em> retried: a sold-out
 * night throws {@code InventoryUnavailableException}, which is an answer, not a failure, and
 * retrying it would just be a slower way of returning the same thing.
 *
 * <p>Uses Spring Framework 7's own {@code @Retryable} rather than Resilience4j — it is in the
 * framework, and it covers this case completely.
 *
 * <p>Idempotency (design doc 8a) lives inside {@link BookingCreator}, not here, for the same
 * reason the transaction does: {@code IdempotencyService.begin} runs {@code REQUIRES_NEW} and
 * commits independently of the retried transaction, so it must sit inside the unit that is
 * retried as a whole, alongside the business logic it guards — exactly where {@code
 * PaymentService.pay} and {@code CancellationService.cancel} keep theirs.
 */
@Service
public class BookingService {

    private final BookingCreator bookingCreator;

    public BookingService(BookingCreator bookingCreator) {
        this.bookingCreator = bookingCreator;
    }

    @Retryable(
            includes = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
            maxRetries = 3,
            delay = 25,
            jitter = 25,
            multiplier = 2.0,
            maxDelay = 250,
            timeUnit = TimeUnit.MILLISECONDS)
    public BookingResponse create(RequestMeta meta, CreateBookingRequest request) {
        return bookingCreator.create(meta, request);
    }

    public BookingResponse find(String bookingUid) {
        return bookingCreator.find(bookingUid);
    }
}
