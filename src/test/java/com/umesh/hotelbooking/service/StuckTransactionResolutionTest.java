package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.ReconciliationRunResponse;
import com.umesh.hotelbooking.dto.ResolveManualReviewRequest;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.config.PaymentStatusCheckProperties;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.ReversalStore;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 15.1 tests 2, 3 and 4, and design doc 17.1's "clearest payments-experience signal in the
 * project": the whole 7.6 stuck-transaction path, resolved for real rather than described.
 *
 * <p>Three scenarios, one {@link MutableClock}-driven fixture support class, exactly as the
 * task spec asks. All three lean on {@link SimulatedOutcome} exactly the way its own Javadoc
 * says it exists to be used: {@code STUCK_THEN_SETTLE} for the late-settlement/reversal path
 * (a and, along the way, c), {@code STUCK_FOREVER} for ladder exhaustion (b).
 *
 * <p>A note on scenario (a)'s timing, because the task spec's own wording differs from what
 * the code actually does: advancing to the auto-reversal-deadline with a SETTLED response
 * does <em>not</em> produce a reversal here. {@code PaymentReconciliationService.reconcileOne}
 * checks the deadline <em>before</em> it ever polls the gateway ("presumed failure takes
 * precedence over the ladder... regardless of how many attempts remain") — so a deadline that
 * has already passed short-circuits straight to a presumed failure and never learns the
 * gateway would have said SETTLED. The reversal path this test drives instead is the one
 * {@code AbstractMockProvider}'s own Javadoc names for {@code STUCK_THEN_SETTLE}: a late
 * SETTLED answer arriving at a normal, in-ladder poll <em>after</em> the 15-minute
 * inventory-hold-window release but comfortably <em>before</em> the deadline — which is what
 * "late settlement after an expired hold" (design doc 16.3/16.4's own phrase) actually names.
 * See PROJECT_STRUCTURE.txt.txt 16.8 for the full account, including why the shipped
 * {@code auto-reversal-deadline} needed to change for scenario (b) to be reachable at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "payment.reconciliation.enabled=false"
})
class StuckTransactionResolutionTest extends AbstractBookingConcurrencyTestSupport {

    private static final Instant START = Instant.parse("2026-09-08T00:00:00Z");

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentReconciliationService reconciliationService;
    @Autowired
    private PaymentStore paymentStore;
    @Autowired
    private BookingStore bookingStore;
    @Autowired
    private ReversalStore reversalStore;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private PaymentStatusCheckProperties statusCheckProperties;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private RequestMeta payMeta() {
        return new RequestMeta(UUID.randomUUID().toString(), ApiType.PAY_BOOKING, UUID.randomUUID().toString());
    }

    private Payment paymentOf(String paymentUid) {
        return paymentStore.findByPaymentUid(paymentUid).orElseThrow();
    }

    private BookingState bookingStateOf(String bookingUid) {
        return bookingStore.findByBookingUid(bookingUid).orElseThrow().getState();
    }

    /**
     * (a) Late settlement after the hold window releases the room, and (c) that the released
     * room is genuinely rebookable while the first payment is still unresolved - not merely a
     * boolean flag flipped.
     */
    @Test
    void lateSettlementAfterTheHoldWindowReleaseReversesTheBookingAndFreesTheRoomForSomeoneElse() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Late Settlement Hotel", 1);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));

        PaymentResponse initiated = paymentService.pay(booking.bookingUid(), payMeta(),
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.STUCK_THEN_SETTLE));
        assertThat(initiated.state()).isEqualTo(PaymentState.UNKNOWN);

        // T+15m1s: past the inventory-hold-window. The first status poll (scheduled T+30s) is
        // also due by now and is STUCK_THEN_SETTLE's poll #1 - still PENDING.
        clock().advance(statusCheckProperties.inventoryHoldWindow().plusSeconds(1));
        ReconciliationRunResponse firstPass = reconciliationService.run();
        assertThat(firstPass.inventoryReleasedOnHoldWindow()).isEqualTo(1);

        Payment afterFirstPass = paymentOf(initiated.paymentUid());
        assertThat(afterFirstPass.isInventoryReleased()).isTrue();
        assertThat(afterFirstPass.getState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(bookingStateOf(booking.bookingUid()))
                .as("the room is released; the guest still sees pending, not failed")
                .isEqualTo(BookingState.PAYMENT_UNKNOWN);

        // Test 4: the released room-night is bookable again by a second guest right now, while
        // the first payment is still unresolved.
        BookingResponse secondGuest = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));
        assertThat(secondGuest.bookingUid()).isNotEqualTo(booking.bookingUid());

        // Advance to poll #2 of STUCK_THEN_SETTLE, which now reports SETTLED - late.
        Payment beforeSecondPoll = paymentOf(initiated.paymentUid());
        clock().setTo(beforeSecondPoll.getNextAttemptAt().plusSeconds(1));
        reconciliationService.run();

        Payment settled = paymentOf(initiated.paymentUid());
        assertThat(settled.getState()).isEqualTo(PaymentState.SETTLED);
        assertThat(bookingStateOf(booking.bookingUid()))
                .as("money settled after the room was already given back to sale - reverse, don't confirm")
                .isEqualTo(BookingState.REVERSED);

        Booking reversedBooking = bookingStore.findByBookingUid(booking.bookingUid()).orElseThrow();
        List<LedgerEntry> entries = ledgerService.findByBooking(reversedBooking.getId());
        assertThat(entries).extracting(LedgerEntry::getType)
                .containsExactlyInAnyOrder(EntryType.CHARGE, EntryType.REVERSAL);
        BigDecimal charged = sumByType(entries, EntryType.CHARGE);
        BigDecimal reversed = sumByType(entries, EntryType.REVERSAL);
        assertThat(charged).as("charge and reversal must net to zero").isEqualByComparingTo(reversed);

        List<Reversal> reversals = reversalStore.findByBookingId(reversedBooking.getId());
        assertThat(reversals).hasSize(1);
        assertThat(reversals.get(0).getReason()).isEqualTo(ReversalReason.LATE_SUCCESS_ON_EXPIRED_BOOKING);
    }

    private record ManualReviewFixture(String bookingUid, String paymentUid) {
    }

    /** (b) The ladder exhausts with no resolution, lands on MANUAL_REVIEW, and the admin exit works. */
    @Test
    void attemptsExhaustedMovesToManualReviewAndTheAdminResolutionEndpointWorks() {
        clock().setTo(START);
        ManualReviewFixture toSettle = driveOneBookingToManualReview("Manual Review Settle Hotel");
        clock().setTo(START);
        ManualReviewFixture toFail = driveOneBookingToManualReview("Manual Review Fail Hotel");
        clock().setTo(START);
        ManualReviewFixture toReject = driveOneBookingToManualReview("Manual Review Reject Hotel");

        // The endpoint rejects an outcome that is neither SETTLED nor FAILED.
        assertThatThrownBy(() -> reconciliationService.resolveManualReview(
                toReject.paymentUid(), new ResolveManualReviewRequest(PaymentState.PROCESSING, "typo"), "corr-reject"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(paymentOf(toReject.paymentUid()).getState())
                .as("a rejected resolution attempt must not move the payment").isEqualTo(PaymentState.MANUAL_REVIEW);

        // By the time exhaustion is reached (~3h in), the 15-minute hold window is long past,
        // so the room was already released - a SETTLED resolution now cannot re-confirm the
        // original booking; it must reverse the late charge instead.
        PaymentResponse settleResponse = reconciliationService.resolveManualReview(
                toSettle.paymentUid(), new ResolveManualReviewRequest(PaymentState.SETTLED, "gateway confirmed by phone"),
                "corr-settle");
        assertThat(settleResponse.state()).isEqualTo(PaymentState.SETTLED);
        Booking settledBooking = bookingStore.findByBookingUid(toSettle.bookingUid()).orElseThrow();
        assertThat(settledBooking.getState()).isEqualTo(BookingState.REVERSED);
        List<LedgerEntry> settleEntries = ledgerService.findByBooking(settledBooking.getId());
        assertThat(settleEntries).extracting(LedgerEntry::getType)
                .containsExactlyInAnyOrder(EntryType.CHARGE, EntryType.REVERSAL);

        PaymentResponse failResponse = reconciliationService.resolveManualReview(
                toFail.paymentUid(), new ResolveManualReviewRequest(PaymentState.FAILED, "confirmed declined"),
                "corr-fail");
        assertThat(failResponse.state()).isEqualTo(PaymentState.FAILED);
        Booking failedBooking = bookingStore.findByBookingUid(toFail.bookingUid()).orElseThrow();
        assertThat(failedBooking.getState()).isEqualTo(BookingState.PAYMENT_FAILED);
        assertThat(ledgerService.findByBooking(failedBooking.getId()))
                .as("a payment that never settled must never write a CHARGE").isEmpty();
    }

    /**
     * Onboards a fresh property, pays with {@code STUCK_FOREVER}, and walks the clock through
     * every configured status-check interval (read from {@link PaymentStatusCheckProperties},
     * never hardcoded) until the ladder exhausts into {@code MANUAL_REVIEW}.
     */
    private ManualReviewFixture driveOneBookingToManualReview(String propertyLabel) {
        Fixture fixture = onboardRoomType(propertyLabel, 1);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));
        PaymentResponse initiated = paymentService.pay(booking.bookingUid(), payMeta(),
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.STUCK_FOREVER));

        for (int attempt = 1; attempt <= statusCheckProperties.maxAttempts(); attempt++) {
            Payment before = paymentOf(initiated.paymentUid());
            clock().setTo(before.getNextAttemptAt().plusSeconds(1));
            reconciliationService.run();
            Payment after = paymentOf(initiated.paymentUid());
            assertThat(after.getAttemptNo()).isEqualTo(attempt);
            assertThat(after.getState()).isEqualTo(PaymentState.UNKNOWN);
        }

        Payment exhausted = paymentOf(initiated.paymentUid());
        clock().setTo(exhausted.getNextAttemptAt().plusSeconds(1));
        reconciliationService.run();

        Payment reviewed = paymentOf(initiated.paymentUid());
        assertThat(reviewed.getState()).isEqualTo(PaymentState.MANUAL_REVIEW);
        return new ManualReviewFixture(booking.bookingUid(), initiated.paymentUid());
    }

    private static BigDecimal sumByType(List<LedgerEntry> entries, EntryType type) {
        return entries.stream()
                .filter(entry -> entry.getType() == type)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
