package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.SweepResponse;
import com.umesh.hotelbooking.service.BookingSweeper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Triggers a hold-expiry and completion sweep on demand (design doc 11, admin endpoints).
 *
 * <p>Exists so the behaviour can be demonstrated and operated without waiting for the
 * schedule — a sweep that can only be observed by waiting a minute is a sweep nobody checks.
 */
@RestController
@RequestMapping("/api/v1/admin/sweeper")
public class SweeperAdminController {

    private final BookingSweeper bookingSweeper;

    public SweeperAdminController(BookingSweeper bookingSweeper) {
        this.bookingSweeper = bookingSweeper;
    }

    @PostMapping("/run")
    public SweepResponse run() {
        return bookingSweeper.sweep();
    }
}
