package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.EmptyPayload;
import com.umesh.hotelbooking.dto.GuestResponse;
import com.umesh.hotelbooking.dto.RedactGuestResponse;
import com.umesh.hotelbooking.service.GuestRedactionService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guest profile lookup and erasure (design doc 11.4, 12.6.3). */
@RestController
@RequestMapping("/api/v1/admin/guests/{guestUid}")
@RequireRole(Role.ADMIN)
public class GuestAdminController {

    private final GuestRedactionService guestRedactionService;
    private final ApiContext apiContext;

    public GuestAdminController(GuestRedactionService guestRedactionService, ApiContext apiContext) {
        this.guestRedactionService = guestRedactionService;
        this.apiContext = apiContext;
    }

    @GetMapping
    @Api(ApiType.GET_GUEST)
    public GuestResponse get(@PathVariable String guestUid) {
        return guestRedactionService.find(guestUid);
    }

    @PostMapping("/redact")
    @Api(ApiType.REDACT_GUEST)
    public RedactGuestResponse redact(@PathVariable String guestUid,
                                      @Valid @RequestBody ApiRequest<EmptyPayload> request) {
        return guestRedactionService.redact(guestUid, apiContext.toRequestMeta(), request.payload());
    }
}
