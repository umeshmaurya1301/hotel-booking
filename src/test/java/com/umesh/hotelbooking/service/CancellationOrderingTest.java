package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.CancelBookingRequest;
import com.umesh.hotelbooking.dto.CancellationResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.PropertyGroup;
import com.umesh.hotelbooking.entity.Refund;
import com.umesh.hotelbooking.entity.RefundState;
import com.umesh.hotelbooking.gateway.GatewayOutcome;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.gateway.RefundRequest;
import com.umesh.hotelbooking.gateway.RefundResult;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RefundRepository;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Design doc 9.5 says "ordering matters", and 16.4 records that the cancellation flow was
 * "transcribed step-for-step" - a transcription with no test is a claim, not a proof.
 * {@code CancellationService} is hand-built with every collaborator mocked, exactly as {@code
 * ReservationOrderingTest} already does for the reservation path, so an {@link InOrder}
 * verification can pin the one property that matters: inventory is released <em>before</em>
 * the gateway refund call, and a refund the gateway rejects still leaves that release standing.
 */
class CancellationOrderingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);

    private BookingRepository bookingRepository;
    private PropertyRepository propertyRepository;
    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;
    private LedgerService ledgerService;
    private InventoryReservationService reservationService;
    private PaymentGatewayRouter router;
    private IdempotencyService idempotencyService;
    private ApplicationEventPublisher eventPublisher;
    private PaymentGatewayProvider provider;
    private CancellationService cancellationService;

    private Booking booking;

    private void setUp(GatewayOutcome refundOutcome) {
        bookingRepository = mock(BookingRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        refundRepository = mock(RefundRepository.class);
        ledgerService = mock(LedgerService.class);
        reservationService = mock(InventoryReservationService.class);
        router = mock(PaymentGatewayRouter.class);
        idempotencyService = mock(IdempotencyService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        provider = mock(PaymentGatewayProvider.class);

        booking = Booking.builder()
                .id(1L).bookingUid("bk-1").propertyId(10L).roomTypeId(20L)
                .checkIn(LocalDate.of(2026, 9, 20)).checkOut(LocalDate.of(2026, 9, 21))
                .units(1).adults(1).children(0)
                .totalAmount(new BigDecimal("1000.00")).currency("INR")
                .holdExpiresAt(Instant.parse("2026-09-08T01:00:00Z"))
                .state(BookingState.CONFIRMED)
                .build();
        Payment settledPayment = Payment.builder()
                .id(5L).bookingId(1L).providerCode("MOCK_CARD").providerReference("ref-5")
                .amount(new BigDecimal("1000.00")).currency("INR").state(PaymentState.SETTLED)
                .build();
        PropertyGroup group = PropertyGroup.builder()
                .refundPolicyCode(FullRefundBefore48Hours.CODE).build();
        Property property = Property.builder().id(10L).propertyGroup(group).zoneId("Asia/Kolkata").build();

        when(idempotencyService.begin(any(RequestMeta.class), any(), eq(CancellationResponse.class)))
                .thenReturn(Optional.empty());
        when(bookingRepository.findByBookingUid("bk-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(1L)).thenReturn(List.of(settledPayment));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        // Mirrors the real repository's @PrePersist: a hand-built Refund never went through
        // JPA's own lifecycle callback, so its state would otherwise stay null.
        when(refundRepository.save(any())).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            if (refund.getState() == null) {
                refund.setState(RefundState.REQUESTED);
            }
            if (refund.getRefundUid() == null) {
                refund.setRefundUid(UUID.randomUUID().toString());
            }
            return refund;
        });
        when(router.routeByProviderCode("MOCK_CARD")).thenReturn(provider);
        when(provider.refund(any(RefundRequest.class)))
                .thenReturn(new RefundResult(refundOutcome, "rfnd-ref", "message"));

        cancellationService = new CancellationService(bookingRepository, propertyRepository, paymentRepository,
                refundRepository, new RefundPolicyFactory(List.of(new FullRefundBefore48Hours())), ledgerService,
                reservationService, router, idempotencyService, eventPublisher, CLOCK);
    }

    private RequestMeta meta() {
        return new RequestMeta(UUID.randomUUID().toString(), ApiType.CANCEL_BOOKING, UUID.randomUUID().toString());
    }

    @Test
    void inventoryIsReleasedBeforeTheGatewayRefundCall() {
        setUp(GatewayOutcome.SETTLED);

        cancellationService.cancel("bk-1", meta(), new CancelBookingRequest(null));

        InOrder order = inOrder(reservationService, provider);
        order.verify(reservationService).release(eq(20L), any(), eq(1));
        order.verify(provider).refund(any(RefundRequest.class));
    }

    @Test
    void aGatewayRefundFailureStillLeavesTheInventoryReleased() {
        setUp(GatewayOutcome.FAILED);

        CancellationResponse response = cancellationService.cancel("bk-1", meta(), new CancelBookingRequest(null));

        verify(reservationService).release(eq(20L), any(), eq(1));
        verify(provider).refund(any(RefundRequest.class));
        assertThat(booking.getState())
                .as("the booking's fate is decided independently of whether money actually moved back")
                .isEqualTo(BookingState.CANCELLED);
        assertThat(response.bookingState()).isEqualTo(BookingState.CANCELLED);
    }
}
