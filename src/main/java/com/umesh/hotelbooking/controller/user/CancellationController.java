package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.CancelBookingRequest;
import com.umesh.hotelbooking.dto.CancellationResponse;
import com.umesh.hotelbooking.service.CancellationService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest-facing cancellation (design doc 9.5, POST /api/v1/user/bookings/{id}/cancel).
 *
 * <p>The body was previously optional, with {@code CancelBookingRequest} defaulted when
 * absent — that only worked because {@code msgId} lived inside the DTO and was itself
 * optional. Now that {@code msgId} is the envelope's {@code @NotBlank} idempotency key, a
 * cancel request always needs a real envelope; there is no longer a meaningful "no body" case.
 */
@RestController
@RequestMapping("/api/v1/user/bookings/{bookingUid}")
@RequireRole(Role.USER)
public class CancellationController {

    private final CancellationService cancellationService;
    private final ApiContext apiContext;

    public CancellationController(CancellationService cancellationService, ApiContext apiContext) {
        this.cancellationService = cancellationService;
        this.apiContext = apiContext;
    }

    @PostMapping("/cancel")
    @Api(ApiType.CANCEL_BOOKING)
    public CancellationResponse cancel(@PathVariable String bookingUid,
                                       @Valid @RequestBody ApiRequest<CancelBookingRequest> request) {
        return cancellationService.cancel(bookingUid, apiContext.toRequestMeta(), request.payload());
    }
}
