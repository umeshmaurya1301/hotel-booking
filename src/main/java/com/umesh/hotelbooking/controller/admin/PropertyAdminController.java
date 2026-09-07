package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.UpdatePropertyRequest;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
public class PropertyAdminController {

    private final PropertyOnboardingService propertyOnboardingService;

    public PropertyAdminController(PropertyOnboardingService propertyOnboardingService) {
        this.propertyOnboardingService = propertyOnboardingService;
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> onboard(@Valid @RequestBody OnboardPropertyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyOnboardingService.onboard(request));
    }

    @PatchMapping("/{propertyUid}")
    public PropertyResponse update(@PathVariable String propertyUid,
                                   @Valid @RequestBody UpdatePropertyRequest request) {
        return propertyOnboardingService.update(propertyUid, request);
    }

    @GetMapping("/{propertyUid}")
    public PropertyResponse get(@PathVariable String propertyUid) {
        return propertyOnboardingService.findByUid(propertyUid);
    }
}
