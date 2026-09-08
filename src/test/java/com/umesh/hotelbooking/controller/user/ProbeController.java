package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.PendingAware;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequireRole;
import com.umesh.hotelbooking.web.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Test-only endpoints for exercising the API-layer plumbing (envelopes, correlation, role
 * checks) in isolation from real business services. Lives under {@code
 * com.umesh.hotelbooking.controller.user} deliberately (moved here in Phase 7, when {@code
 * ResponseEnvelopeAdvice}'s {@code basePackages} narrowed to exclude the bare {@code
 * controller} package and {@code controller.webhook} — see that class's Javadoc), so the
 * {@code basePackages}-scoped advices apply to it exactly as they do to the real controllers.
 */
@RestController
@RequestMapping("/api/v1/test")
public class ProbeController {

    @GetMapping("/success")
    @Api(ApiType.UNKNOWN)
    public ProbeResponse success() {
        return new ProbeResponse("ok");
    }

    @GetMapping("/pending")
    @Api(ApiType.UNKNOWN)
    public ProbePendingResponse pending() {
        return new ProbePendingResponse(true);
    }

    @GetMapping("/not-pending")
    @Api(ApiType.UNKNOWN)
    public ProbePendingResponse notPending() {
        return new ProbePendingResponse(false);
    }

    @GetMapping("/pending-list")
    @Api(ApiType.UNKNOWN)
    public List<ProbePendingResponse> pendingList() {
        return List.of(new ProbePendingResponse(true));
    }

    @GetMapping("/domain-error")
    @Api(ApiType.UNKNOWN)
    public ProbeResponse domainError() {
        throw new BookingNotFoundException("no-such-booking");
    }

    @PostMapping("/validate")
    @Api(ApiType.UNKNOWN)
    public ProbeResponse validate(@Valid @RequestBody ApiRequest<ProbePayload> request) {
        return new ProbeResponse(request.payload().requiredField());
    }

    @GetMapping("/boom")
    @Api(ApiType.UNKNOWN)
    public ProbeResponse boom() {
        throw new IllegalStateException("jdbc:h2:mem:secret connection string leaked here");
    }

    @GetMapping("/admin-only")
    @Api(ApiType.UNKNOWN)
    @RequireRole(Role.ADMIN)
    public ProbeResponse adminOnly() {
        return new ProbeResponse("ok");
    }

    public record ProbeResponse(String value) {
    }

    public record ProbePendingResponse(boolean flag) implements PendingAware {
        @Override
        public boolean pending() {
            return flag;
        }
    }

    public record ProbePayload(@NotBlank String requiredField) {
    }
}
