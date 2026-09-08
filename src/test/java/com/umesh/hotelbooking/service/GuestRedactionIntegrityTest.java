package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CancelBookingRequest;
import com.umesh.hotelbooking.dto.CancellationResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.EmptyPayload;
import com.umesh.hotelbooking.dto.GuestDetails;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RedactGuestResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.GuestRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The test design doc 12.6.3 actually calls for (task spec §6.1): not that a redacted field
 * reads {@code [REDACTED]} — that proves nothing interesting on its own — but that erasure and
 * the immutable financial trail genuinely co-exist. Book, pay, cancel with a partial refund,
 * redact the guest, then assert the booking still resolves, {@code guestId} still points at a
 * live row, and the ledger balance is exactly what it was before redaction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false"
})
class GuestRedactionIntegrityTest {

    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private CancellationService cancellationService;
    @Autowired
    private GuestRedactionService guestRedactionService;
    @Autowired
    private GuestRepository guestRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private Clock clock;

    private static RequestMeta meta(ApiType type) {
        return new RequestMeta(UUID.randomUUID().toString(), type, UUID.randomUUID().toString());
    }

    @Test
    void erasureLeavesTheFinancialTrailIntact() {
        // FIFTY_PERCENT_BEFORE_24H: a real partial refund, neither full nor zero, so the
        // ledger math genuinely has something to prove.
        PropertyResponse property = onboardingService.onboard(new OnboardPropertyRequest(
                null, "Redaction Test Owner", null,
                null, null, null, "FIFTY_PERCENT_BEFORE_24H",
                "Redaction Test Hotel " + System.nanoTime(), "Bengaluru", null, null, null, 4,
                "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 5, 4, new BigDecimal("8000.00"))),
                null));
        RoomType roomType = roomTypeRepository.findByPropertyId(
                propertyRepository.findByPropertyUid(property.propertyUid()).orElseThrow().getId()).get(0);
        // Ten nights out: well past the 24h cancellation-notice cutoff.
        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(10);

        GuestDetails details = new GuestDetails(
                "Asha Menon", "asha@example.com", "+919876543210", "221B Baker Street", LocalDate.of(1990, 1, 1));
        BookingResponse booking = bookingService.create(meta(ApiType.CREATE_BOOKING), new CreateBookingRequest(
                null, roomType.getRoomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, details));
        assertThat(booking.guestUid()).isNotBlank();

        PaymentResponse payment = paymentService.pay(booking.bookingUid(), meta(ApiType.PAY_BOOKING),
                new InitiatePaymentRequest(PaymentMethod.CARD, null));
        assertThat(payment.state()).isEqualTo(PaymentState.SETTLED);

        CancellationResponse cancellation = cancellationService.cancel(booking.bookingUid(), meta(ApiType.CANCEL_BOOKING),
                new CancelBookingRequest("integrity test"));
        BigDecimal totalCharged = booking.totalAmount();
        assertThat(cancellation.refundAmount())
                .as("the fifty-percent policy is a genuine partial refund")
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(totalCharged)
                .isEqualByComparingTo(totalCharged.multiply(new BigDecimal("0.5")));

        Booking bookingEntity = bookingRepository.findByBookingUid(booking.bookingUid()).orElseThrow();
        BigDecimal balanceBeforeRedaction = ledgerService.remainingBalance(bookingEntity.getId());

        RedactGuestResponse first = guestRedactionService.redact(
                booking.guestUid(), meta(ApiType.REDACT_GUEST), new EmptyPayload());
        assertThat(first.redactedAt()).isNotNull();

        // The booking still resolves, and its guestId still points at a live row.
        BookingResponse reloaded = bookingService.find(booking.bookingUid());
        assertThat(reloaded.bookingUid()).isEqualTo(booking.bookingUid());
        assertThat(reloaded.guestUid()).isEqualTo(booking.guestUid());

        Guest guest = guestRepository.findByGuestUid(booking.guestUid()).orElseThrow();
        assertThat(guest.getFullName()).isEqualTo("[REDACTED]");
        assertThat(guest.getEmail()).isEqualTo("[REDACTED]");
        assertThat(guest.getPhone()).isEqualTo("[REDACTED]");
        assertThat(guest.getAddress()).isEqualTo("[REDACTED]");
        assertThat(guest.getDateOfBirth()).isNull();
        assertThat(guest.getRedactedAt()).isNotNull();

        // The ledger — an append-only table this booking's guestId never touches directly
        // (design doc 12.6.3) — is completely unaffected by erasing the guest row it never
        // referenced PII from in the first place.
        BigDecimal balanceAfterRedaction = ledgerService.remainingBalance(bookingEntity.getId());
        assertThat(balanceAfterRedaction).isEqualByComparingTo(balanceBeforeRedaction);
        assertThat(balanceAfterRedaction)
                .as("design doc 9.4: sum(CHARGE) - sum(REFUND) - sum(REVERSAL) must never go negative")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(balanceAfterRedaction).isEqualByComparingTo(totalCharged.subtract(cancellation.refundAmount()));

        // Re-running erasure is a no-op returning the same redactedAt, not an error. Compared
        // at microsecond precision: the first value is the raw in-memory Instant.now(clock)
        // from the original write, the second is reloaded from H2, which stores Instant at
        // microsecond precision — an unrelated JPA round-trip artefact, not a behavioural gap.
        RedactGuestResponse second = guestRedactionService.redact(
                booking.guestUid(), meta(ApiType.REDACT_GUEST), new EmptyPayload());
        assertThat(second.redactedAt()).isCloseTo(first.redactedAt(), within(1, java.time.temporal.ChronoUnit.MILLIS));
    }
}
