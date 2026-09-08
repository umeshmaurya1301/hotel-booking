package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.ExtendHorizonRequest;
import com.umesh.hotelbooking.dto.InventoryResponse;
import com.umesh.hotelbooking.dto.MaterialisationResponse;
import com.umesh.hotelbooking.dto.RateOverrideRequest;
import com.umesh.hotelbooking.dto.RepriceRequest;
import com.umesh.hotelbooking.dto.RepriceResponse;
import com.umesh.hotelbooking.service.InventoryAdminService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Inventory administration: roll the horizon forward, reprice a range under a strategy, or
 * override a single night.
 *
 * <p>The reprice endpoint is what makes {@code PricingStrategy} demonstrably pluggable
 * rather than a one-shot at onboarding (design doc 4.2.1).
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequireRole(Role.ADMIN)
public class InventoryAdminController {

    private final InventoryAdminService inventoryAdminService;

    public InventoryAdminController(InventoryAdminService inventoryAdminService) {
        this.inventoryAdminService = inventoryAdminService;
    }

    @PostMapping("/extend")
    @Api(ApiType.EXTEND_INVENTORY)
    public MaterialisationResponse extend(@Valid @RequestBody ApiRequest<ExtendHorizonRequest> request) {
        return inventoryAdminService.extendHorizon(request.payload());
    }

    @PostMapping("/reprice")
    @Api(ApiType.REPRICE_INVENTORY)
    public RepriceResponse reprice(@Valid @RequestBody ApiRequest<RepriceRequest> request) {
        return inventoryAdminService.reprice(request.payload());
    }

    @PatchMapping("/{roomTypeUid}")
    @Api(ApiType.OVERRIDE_INVENTORY)
    public InventoryResponse override(@PathVariable String roomTypeUid,
                                      @Valid @RequestBody ApiRequest<RateOverrideRequest> request) {
        return inventoryAdminService.overrideNight(roomTypeUid, request.payload());
    }

    @GetMapping("/{roomTypeUid}")
    @Api(ApiType.VIEW_INVENTORY)
    public List<InventoryResponse> view(
            @PathVariable String roomTypeUid,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return inventoryAdminService.view(roomTypeUid, from, to);
    }
}
