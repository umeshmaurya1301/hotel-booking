package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ManualReversalRequest;
import com.umesh.hotelbooking.dto.ReversalResponse;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.service.ReversalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual reversal of a settled booking (design doc 9.2 MANUAL_CORRECTION). */
@RestController
@RequestMapping("/api/v1/admin/bookings/{bookingUid}")
public class ReversalAdminController {

    private final ReversalService reversalService;

    public ReversalAdminController(ReversalService reversalService) {
        this.reversalService = reversalService;
    }

    @PostMapping("/reverse")
    public ReversalResponse reverse(@PathVariable String bookingUid, @Valid @RequestBody ManualReversalRequest request) {
        Reversal reversal = reversalService.reverseBooking(bookingUid, ReversalReason.MANUAL_CORRECTION);
        return ReversalResponse.from(reversal, bookingUid);
    }
}
