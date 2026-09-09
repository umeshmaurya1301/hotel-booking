package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.CircuitBreakerProperties;
import com.umesh.hotelbooking.config.PaymentStatusCheckProperties;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.PropertyGroup;
import com.umesh.hotelbooking.gateway.CircuitBreakerOpenException;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.PaymentCircuitBreaker;
import com.umesh.hotelbooking.gateway.PaymentGatewayClient;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 15.1 test 1: the breaker never guesses (design doc 7.1/7.2). Two layers, deliberately kept
 * apart: {@link PaymentCircuitBreaker} on its own (no Spring context — it needs only a {@link
 * CircuitBreakerProperties} and a {@link Clock}), and {@link PaymentService} hand-built with
 * every collaborator mocked except the breaker's own contract, to prove the fallback a
 * breaker-open call produces is {@code UNKNOWN} and nothing else.
 */
class PaymentCircuitBreakerTest {

    private static final CircuitBreakerProperties PROPERTIES =
            new CircuitBreakerProperties(4, 50, Duration.ofSeconds(30), 2);

    private PaymentCircuitBreaker breakerWith(MutableClock clock) {
        return new PaymentCircuitBreaker(PROPERTIES, clock);
    }

    private PaymentGatewayProvider providerMock() {
        return mock(PaymentGatewayProvider.class);
    }

    @Test
    void failuresBelowTheThresholdKeepTheBreakerClosed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);

        // window of 4, only the last one a failure: 1/4 = 25% < 50% threshold.
        breaker.execute(() -> "ok");
        breaker.execute(() -> "ok");
        breaker.execute(() -> "ok");
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("simulated");
        })).isInstanceOf(GatewayTimeoutException.class);

        assertThat(breaker.currentState()).isEqualTo("CLOSED");
    }

    @Test
    void crossingTheFailureRateThresholdOverTheWindowOpensTheBreaker() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);

        // window of 4, 2 failures: the second failure (call 4) brings the rate to exactly 50%.
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("simulated");
        })).isInstanceOf(GatewayTimeoutException.class);
        breaker.execute(() -> "ok");
        breaker.execute(() -> "ok");
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("simulated");
        })).isInstanceOf(GatewayTimeoutException.class);

        assertThat(breaker.currentState()).isEqualTo("OPEN");
    }

    @Test
    void whileOpenACallThrowsWithoutEverInvokingTheProvider() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);
        openTheBreaker(breaker);
        assertThat(breaker.currentState()).isEqualTo("OPEN");

        PaymentGatewayProvider provider = providerMock();
        assertThatThrownBy(() -> breaker.execute(() -> provider.providerCode()))
                .isInstanceOf(CircuitBreakerOpenException.class);

        // The failure mode worth catching: a breaker that still calls through.
        verifyNoInteractions(provider);
    }

    @Test
    void afterTheWaitDurationTheBreakerAdmitsTheConfiguredNumberOfHalfOpenProbes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);
        openTheBreaker(breaker);

        clock.advance(PROPERTIES.waitDurationInOpenState().plusSeconds(1));

        // permittedCallsInHalfOpenState is 2: both probes must be admitted (no throw).
        assertThat(breaker.execute(() -> "probe-1")).isEqualTo("probe-1");
        assertThat(breaker.execute(() -> "probe-2")).isEqualTo("probe-2");
        assertThat(breaker.currentState()).isEqualTo("CLOSED");
    }

    @Test
    void aSuccessfulHalfOpenProbeClosesTheBreaker() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);
        openTheBreaker(breaker);
        clock.advance(PROPERTIES.waitDurationInOpenState().plusSeconds(1));

        breaker.execute(() -> "probe-1");
        breaker.execute(() -> "probe-2");

        assertThat(breaker.currentState()).isEqualTo("CLOSED");
        // Closed for real, not just reporting it: a subsequent call is admitted without a trial limit.
        assertThat(breaker.execute(() -> "post-close")).isEqualTo("post-close");
    }

    @Test
    void aFailingHalfOpenProbeReOpensTheBreakerImmediately() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        PaymentCircuitBreaker breaker = breakerWith(clock);
        openTheBreaker(breaker);
        clock.advance(PROPERTIES.waitDurationInOpenState().plusSeconds(1));

        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("still unwell");
        })).isInstanceOf(GatewayTimeoutException.class);

        assertThat(breaker.currentState())
                .as("one failed trial re-opens regardless of how many probes were permitted")
                .isEqualTo("OPEN");
    }

    private void openTheBreaker(PaymentCircuitBreaker breaker) {
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("simulated");
        })).isInstanceOf(GatewayTimeoutException.class);
        breaker.execute(() -> "ok");
        breaker.execute(() -> "ok");
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new GatewayTimeoutException("simulated");
        })).isInstanceOf(GatewayTimeoutException.class);
    }

    // --- PaymentService level: the fallback the breaker's OPEN state actually produces. ---

    @Test
    @SuppressWarnings("unchecked")
    void withTheBreakerForcedOpenPayReturnsUnknownNeverConfirmedNeverFailed() {
        BookingStore bookingStore = mock(BookingStore.class);
        PropertyStore propertyStore = mock(PropertyStore.class);
        PaymentStore paymentStore = mock(PaymentStore.class);
        PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
        PaymentGatewayClient gatewayClient = mock(PaymentGatewayClient.class);
        PaymentCircuitBreaker circuitBreaker = mock(PaymentCircuitBreaker.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        LedgerService ledgerService = mock(LedgerService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);

        PropertyGroup group = PropertyGroup.builder().settlementBankCode("HDFC").build();
        Property property = Property.builder().id(10L).propertyGroup(group).zoneId("Asia/Kolkata").build();
        Booking booking = Booking.builder()
                .id(1L).bookingUid("bk-1").propertyId(10L).roomTypeId(20L)
                .checkIn(LocalDate.of(2026, 9, 10)).checkOut(LocalDate.of(2026, 9, 11))
                .units(1).adults(1).children(0)
                .totalAmount(new BigDecimal("5000.00")).currency("INR")
                .holdExpiresAt(Instant.parse("2026-09-08T01:00:00Z"))
                .state(BookingState.CREATED)
                .build();

        when(bookingStore.findByBookingUid("bk-1")).thenReturn(Optional.of(booking));
        when(propertyStore.findById(10L)).thenReturn(Optional.of(property));
        when(paymentStore.findByBookingId(1L)).thenReturn(List.of());
        when(paymentStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentGatewayProvider provider = providerMock();
        when(provider.providerCode()).thenReturn("MOCK_CARD");
        when(router.route(eq(PaymentMethod.CARD), eq("HDFC"))).thenReturn(provider);
        when(idempotencyService.begin(any(RequestMeta.class), any(), eq(PaymentResponse.class)))
                .thenReturn(Optional.empty());
        when(circuitBreaker.execute(any())).thenThrow(new CircuitBreakerOpenException("open"));

        PaymentService paymentService = new PaymentService(bookingStore, propertyStore, paymentStore,
                router, gatewayClient, circuitBreaker, statusCheckProperties(), idempotencyService,
                ledgerService, clock);

        PaymentResponse response = paymentService.pay("bk-1",
                new RequestMeta("msg-1", com.umesh.hotelbooking.web.ApiType.PAY_BOOKING, "corr-1"),
                new InitiatePaymentRequest(PaymentMethod.CARD, null));

        assertThat(response.state())
                .as("breaker-open must never be guessed as CONFIRMED or FAILED (7.2)")
                .isEqualTo(PaymentState.UNKNOWN);
        assertThat(booking.getState()).isEqualTo(BookingState.PAYMENT_UNKNOWN);
    }

    private static PaymentStatusCheckProperties statusCheckProperties() {
        return new PaymentStatusCheckProperties(null, 0.2, Duration.ofMinutes(15), Duration.ofHours(2));
    }
}
