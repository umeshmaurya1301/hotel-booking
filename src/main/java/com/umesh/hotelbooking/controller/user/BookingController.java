package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.service.BookingService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest-facing booking endpoints.
 *
 * <p>Creating a booking <em>holds</em> the room-nights; it does not sell them. The hold lapses
 * at {@code holdExpiresAt} unless payment completes, and the sweeper puts the nights back on
 * sale when it does.
 */
@RestController
@RequestMapping("/api/v1/user/bookings")
@RequireRole(Role.USER)
public class BookingController {

    private final BookingService bookingService;
    private final ApiContext apiContext;

    public BookingController(BookingService bookingService, ApiContext apiContext) {
        this.bookingService = bookingService;
        this.apiContext = apiContext;
    }

    @PostMapping
    @Api(ApiType.CREATE_BOOKING)
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody ApiRequest<CreateBookingRequest> request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(apiContext.toRequestMeta(), request.payload()));
    }

    @GetMapping("/{bookingUid}")
    @Api(ApiType.GET_BOOKING)
    public BookingResponse get(@PathVariable String bookingUid) {
        return bookingService.find(bookingUid);
    }
}
