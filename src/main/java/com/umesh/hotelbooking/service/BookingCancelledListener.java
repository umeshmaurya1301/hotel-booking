package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.event.BookingCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records completed cancellations. {@code AFTER_COMMIT} for the same reason as
 * {@link PropertyOnboardedListener}: a cancellation that then rolls back must never be
 * announced.
 */
@Component
public class BookingCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Booking cancelled: bookingUid={} refundUid={} refundAmount={} policy={}",
                event.bookingUid(), event.refundUid(), event.refundAmount(), event.refundPolicyCode());
    }
}
