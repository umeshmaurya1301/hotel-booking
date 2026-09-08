package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.service.PaymentService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest-facing payment endpoints (design doc 11, POST /api/v1/user/bookings/{id}/pay).
 *
 * <p>The response's {@code state} can legitimately be {@code PAYMENT_UNKNOWN}-adjacent (the
 * payment itself sits at {@code UNKNOWN}) — that is not a bug in this endpoint, it is the
 * whole point of 7.2: never guess when the gateway didn't answer. {@link
 * com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice} renders that case as
 * {@code PENDING}, not a failure.
 */
@RestController
@RequestMapping("/api/v1/user/bookings/{bookingUid}")
@RequireRole(Role.USER)
public class PaymentController {

    private final PaymentService paymentService;
    private final ApiContext apiContext;

    public PaymentController(PaymentService paymentService, ApiContext apiContext) {
        this.paymentService = paymentService;
        this.apiContext = apiContext;
    }

    @PostMapping("/pay")
    @Api(ApiType.PAY_BOOKING)
    public PaymentResponse pay(@PathVariable String bookingUid, @Valid @RequestBody ApiRequest<InitiatePaymentRequest> request) {
        return paymentService.pay(bookingUid, apiContext.toRequestMeta(), request.payload());
    }

    @GetMapping("/payments/{paymentUid}")
    @Api(ApiType.GET_PAYMENT)
    public PaymentResponse get(@PathVariable String bookingUid, @PathVariable String paymentUid) {
        return paymentService.find(paymentUid);
    }
}
