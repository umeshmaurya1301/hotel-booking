package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.dto.EmptyPayload;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.ReconciliationRunResponse;
import com.umesh.hotelbooking.dto.ResolveManualReviewRequest;
import com.umesh.hotelbooking.service.PaymentReconciliationService;
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

import java.util.List;

/**
 * Payment reconciliation and manual review (design doc 11 admin endpoints, 7.6.3/7.6.4).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequireRole(Role.ADMIN)
public class PaymentAdminController {

    private final PaymentReconciliationService reconciliationService;
    private final ApiContext apiContext;

    public PaymentAdminController(PaymentReconciliationService reconciliationService, ApiContext apiContext) {
        this.reconciliationService = reconciliationService;
        this.apiContext = apiContext;
    }

    @PostMapping("/reconciliation/run")
    @Api(ApiType.RUN_RECONCILIATION)
    public ReconciliationRunResponse runReconciliation(@Valid @RequestBody ApiRequest<EmptyPayload> request) {
        return reconciliationService.run();
    }

    @PostMapping("/payments/{paymentUid}/resolve")
    @Api(ApiType.RESOLVE_MANUAL_REVIEW)
    public PaymentResponse resolve(@PathVariable String paymentUid,
                                   @Valid @RequestBody ApiRequest<ResolveManualReviewRequest> request) {
        return reconciliationService.resolveManualReview(paymentUid, request.payload(), apiContext.getCorrelationId());
    }

    @GetMapping("/payments/stuck")
    @Api(ApiType.LIST_STUCK_PAYMENTS)
    public List<PaymentResponse> stuck() {
        return reconciliationService.listStuck();
    }
}
