package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.entity.GatewayCheckStatus;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PaymentStatusCheckRepository;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 15.1 test 5: our call failing is not the gateway answering (design doc 7.6.4). A {@code
 * GatewayTimeoutException} on the status-check call is an {@code ERROR} — evidence about our
 * own network, not about the transaction — and must not consume the ladder's attempt budget;
 * only a genuine {@code PENDING} answer may. A one-sided test (asserting only that ERROR
 * leaves {@code attemptNo} alone) would pass against a service that never increments it at
 * all, so both directions are asserted here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "payment.reconciliation.enabled=false"
})
class PaymentAttemptBudgetTest extends AbstractBookingConcurrencyTestSupport {

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
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentStatusCheckRepository statusCheckRepository;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private RequestMeta payMeta() {
        return new RequestMeta(UUID.randomUUID().toString(), ApiType.PAY_BOOKING, UUID.randomUUID().toString());
    }

    private String createBooking(String propertyLabel) {
        Fixture fixture = onboardRoomType(propertyLabel, 5);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));
        return booking.bookingUid();
    }

    @Test
    void anErrorFromOurOwnSideLeavesTheAttemptBudgetUntouchedButIsStillRecorded() {
        clock().setTo(START);
        String bookingUid = createBooking("Timeout Hotel");

        // TIMEOUT: the mock provider throws GatewayTimeoutException both at initiate and at
        // every subsequent status poll - our own call never lands, so the transaction is never
        // observed either way.
        PaymentResponse initiated = paymentService.pay(bookingUid, payMeta(),
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.TIMEOUT));
        assertThat(initiated.state()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(paymentRepository.findByPaymentUid(initiated.paymentUid()).orElseThrow().getAttemptNo())
                .isZero();

        clock().advance(Duration.ofSeconds(31));
        reconciliationService.run();

        Payment reloaded = paymentRepository.findByPaymentUid(initiated.paymentUid()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(reloaded.getAttemptNo())
                .as("an ERROR is not evidence about the transaction - it must not consume the ladder's budget")
                .isZero();
        assertThat(reloaded.getNextAttemptAt())
                .as("the schedule still advances even though the attempt count does not")
                .isAfter(clock().instant().minusSeconds(1));

        List<PaymentStatusCheck> checks = statusCheckRepository.findByPaymentIdOrderByAttemptNoAsc(reloaded.getId());
        assertThat(checks).as("an error we did not record is an error we cannot reconstruct").hasSize(1);
        assertThat(checks.get(0).getGatewayStatus()).isEqualTo(GatewayCheckStatus.ERROR);
    }

    @Test
    void aGenuinePendingAnswerAdvancesTheAttemptBudgetAndIsRecorded() {
        clock().setTo(START);
        String bookingUid = createBooking("Stuck Hotel");

        PaymentResponse initiated = paymentService.pay(bookingUid, payMeta(),
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.STUCK_FOREVER));
        assertThat(initiated.state()).isEqualTo(PaymentState.UNKNOWN);

        clock().advance(Duration.ofSeconds(31));
        reconciliationService.run();

        Payment reloaded = paymentRepository.findByPaymentUid(initiated.paymentUid()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(PaymentState.UNKNOWN);
        assertThat(reloaded.getAttemptNo())
                .as("a genuine PENDING answer is an observation and must consume the budget")
                .isEqualTo(1);

        List<PaymentStatusCheck> checks = statusCheckRepository.findByPaymentIdOrderByAttemptNoAsc(reloaded.getId());
        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getGatewayStatus()).isEqualTo(GatewayCheckStatus.PENDING);
        assertThat(checks.get(0).getAttemptNo()).isEqualTo(1);
    }
}
