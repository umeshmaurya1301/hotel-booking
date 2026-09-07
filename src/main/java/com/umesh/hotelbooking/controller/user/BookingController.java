package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.service.BookingService;
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
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/{bookingUid}")
    public BookingResponse get(@PathVariable String bookingUid) {
        return bookingService.find(bookingUid);
    }
}
