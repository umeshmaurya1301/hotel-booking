package com.umesh.hotelbooking.controller.admin;

import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.ReconciliationRunResponse;
import com.umesh.hotelbooking.dto.ResolveManualReviewRequest;
import com.umesh.hotelbooking.service.PaymentReconciliationService;
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
public class PaymentAdminController {

    private final PaymentReconciliationService reconciliationService;

    public PaymentAdminController(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/reconciliation/run")
    public ReconciliationRunResponse runReconciliation() {
        return reconciliationService.run();
    }

    @PostMapping("/payments/{paymentUid}/resolve")
    public PaymentResponse resolve(@PathVariable String paymentUid, @Valid @RequestBody ResolveManualReviewRequest request) {
        return reconciliationService.resolveManualReview(paymentUid, request);
    }

    @GetMapping("/payments/stuck")
    public List<PaymentResponse> stuck() {
        return reconciliationService.listStuck();
    }
}
