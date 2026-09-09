package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.UpdatePropertyRequest;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Property onboarding and maintenance, for operators.
 *
 * <p>Admin, guest and machine-to-machine callers live in separate packages so the separation
 * is structural rather than a URL convention (design doc 11.4). Authorisation is out of
 * scope per the brief; the role separation is expressed here and enforcement is stubbed.
 */
@RestController
@RequestMapping("/api/v1/admin/properties")
@RequireRole(Role.ADMIN)
public class PropertyAdminController {

    private final PropertyOnboardingService propertyOnboardingService;

    public PropertyAdminController(PropertyOnboardingService propertyOnboardingService) {
        this.propertyOnboardingService = propertyOnboardingService;
    }

    /** {@code @ResponseStatus} rather than a {@code ResponseEntity} for the same reason
     * {@code BookingController.create} uses it — the status is otherwise invisible to anything
     * reading the method's metadata, and was documented as a plain 200 by springdoc until
     * Phase 10 (see DESIGN.md 16.9). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Api(ApiType.ONBOARD_PROPERTY)
    public PropertyResponse onboard(@Valid @RequestBody ApiRequest<OnboardPropertyRequest> request) {
        return propertyOnboardingService.onboard(request.payload());
    }

    @PatchMapping("/{propertyUid}")
    @Api(ApiType.UPDATE_PROPERTY)
    public PropertyResponse update(@PathVariable String propertyUid,
                                   @Valid @RequestBody ApiRequest<UpdatePropertyRequest> request) {
        return propertyOnboardingService.update(propertyUid, request.payload());
    }

    @GetMapping("/{propertyUid}")
    @Api(ApiType.GET_PROPERTY)
    public PropertyResponse get(@PathVariable String propertyUid) {
        return propertyOnboardingService.findByUid(propertyUid);
    }
}
