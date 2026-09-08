package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.ManualReversalRequest;
import com.umesh.hotelbooking.dto.ReversalResponse;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.service.ReversalService;
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

/** Manual reversal of a settled booking (design doc 9.2 MANUAL_CORRECTION). */
@RestController
@RequestMapping("/api/v1/admin/bookings/{bookingUid}")
@RequireRole(Role.ADMIN)
public class ReversalAdminController {

    private final ReversalService reversalService;
    private final ApiContext apiContext;

    public ReversalAdminController(ReversalService reversalService, ApiContext apiContext) {
        this.reversalService = reversalService;
        this.apiContext = apiContext;
    }

    @PostMapping("/reverse")
    @Api(ApiType.MANUAL_REVERSAL)
    public ReversalResponse reverse(@PathVariable String bookingUid,
                                    @Valid @RequestBody ApiRequest<ManualReversalRequest> request) {
        Reversal reversal = reversalService.reverseBooking(
                bookingUid, ReversalReason.MANUAL_CORRECTION, apiContext.getCorrelationId());
        return ReversalResponse.from(reversal, bookingUid);
    }
}
