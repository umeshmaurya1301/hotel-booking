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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * {@code @ResponseStatus} rather than building a {@code ResponseEntity}: both produce the
     * same 201, but only the annotation is visible to anything reading the method's metadata —
     * a status set inside the body was documented by springdoc as a plain 200 until Phase 10
     * changed this (see DESIGN.md 16.9). Returning the plain DTO also matches
     * every other controller in this codebase and design doc 11.2's own rule that controllers
     * hand back payloads and let {@code ResponseEnvelopeAdvice} do the wrapping.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Api(ApiType.CREATE_BOOKING)
    public BookingResponse create(@Valid @RequestBody ApiRequest<CreateBookingRequest> request) {
        return bookingService.create(apiContext.toRequestMeta(), request.payload());
    }

    @GetMapping("/{bookingUid}")
    @Api(ApiType.GET_BOOKING)
    public BookingResponse get(@PathVariable String bookingUid) {
        return bookingService.find(bookingUid);
    }
}
