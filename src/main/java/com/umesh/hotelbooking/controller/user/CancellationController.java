package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.CancelBookingRequest;
import com.umesh.hotelbooking.dto.CancellationResponse;
import com.umesh.hotelbooking.service.CancellationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guest-facing cancellation (design doc 9.5, POST /api/v1/user/bookings/{id}/cancel). */
@RestController
@RequestMapping("/api/v1/user/bookings/{bookingUid}")
public class CancellationController {

    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @PostMapping("/cancel")
    public CancellationResponse cancel(@PathVariable String bookingUid,
                                       @RequestBody(required = false) CancelBookingRequest request) {
        return cancellationService.cancel(bookingUid, request == null ? new CancelBookingRequest(null, null) : request);
    }
}
