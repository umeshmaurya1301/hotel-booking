package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.service.PaymentService;
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
 * whole point of 7.2: never guess when the gateway didn't answer.
 */
@RestController
@RequestMapping("/api/v1/user/bookings/{bookingUid}")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pay")
    public PaymentResponse pay(@PathVariable String bookingUid, @Valid @RequestBody InitiatePaymentRequest request) {
        return paymentService.pay(bookingUid, request);
    }

    @GetMapping("/payments/{paymentUid}")
    public PaymentResponse get(@PathVariable String bookingUid, @PathVariable String paymentUid) {
        return paymentService.find(paymentUid);
    }
}
