package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.dto.SearchResponse;
import com.umesh.hotelbooking.search.PropertySearchService;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest-facing discovery (design doc 10, 11.4, POST /api/v1/user/properties/search).
 *
 * <p>A POST despite being a read: the criteria include a set of amenities and a price range,
 * which as query parameters would be unwieldy and length-limited (URL length limits are real,
 * and a multi-value amenity filter plus two price bounds plus dates adds up quickly). Design
 * doc 11.4 routes it this way explicitly.
 *
 * <p><b>No {@code IdempotencyService} wrapper.</b> Search changes no state, so there is
 * nothing to dedupe — and a stored response would be a stale availability snapshot served as
 * if fresh, which is exactly the failure design doc 10.3 warns about, made worse by caching
 * it. {@code msgId} is still {@code @NotBlank} on the envelope, because the envelope is
 * uniform across every USER-category endpoint; it simply drives nothing here. This mirrors
 * Phase 6's own reasoning for why the sweeper and reconciliation triggers went undeduped (see
 * that phase's 16.5 findings) — a fresh answer, not a replayed one, is the correct response to
 * "run this again".
 */
@RestController
@RequestMapping("/api/v1/user/properties")
@RequireRole(Role.USER)
public class SearchController {

    private final PropertySearchService propertySearchService;

    public SearchController(PropertySearchService propertySearchService) {
        this.propertySearchService = propertySearchService;
    }

    @PostMapping("/search")
    @Api(ApiType.SEARCH_PROPERTIES)
    public SearchResponse search(@Valid @RequestBody ApiRequest<SearchPropertiesRequest> request) {
        return propertySearchService.search(request.payload());
    }
}
