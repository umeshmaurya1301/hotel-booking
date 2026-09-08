package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.LedgerViewResponse;
import com.umesh.hotelbooking.service.LedgerService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/admin/ledger?bookingUid= — the financial trail for one booking (design doc 9.6). */
@RestController
@RequestMapping("/api/v1/admin/ledger")
@RequireRole(Role.ADMIN)
public class LedgerAdminController {

    private final LedgerService ledgerService;

    public LedgerAdminController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @Api(ApiType.VIEW_LEDGER)
    public LedgerViewResponse view(@RequestParam String bookingUid) {
        return ledgerService.viewByBookingUid(bookingUid);
    }
}
