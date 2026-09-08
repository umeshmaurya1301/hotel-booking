package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.exception.UnknownRefundPolicyException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a {@link RefundPolicy} by code (design doc 9.3). Named Factory, not Registry, to
 * match the design document's own term for this exact role — functionally the same shape as
 * {@link PricingStrategyRegistry}: Spring injects every {@code RefundPolicy} bean, so adding
 * a policy is one new {@code @Component} and no change here.
 */
@Component
public class RefundPolicyFactory {

    /** Property groups created without an explicit choice get this policy. */
    public static final String DEFAULT_CODE = FullRefundBefore48Hours.CODE;

    private final java.util.Map<String, RefundPolicy> policiesByCode;

    public RefundPolicyFactory(List<RefundPolicy> policies) {
        this.policiesByCode = policies.stream().collect(Collectors.toMap(
                RefundPolicy::policyCode,
                policy -> policy,
                (first, duplicate) -> {
                    throw new IllegalStateException(
                            "Two refund policies declare the code " + first.policyCode());
                },
                LinkedHashMap::new));
        if (!policiesByCode.containsKey(DEFAULT_CODE)) {
            throw new UnknownRefundPolicyException(DEFAULT_CODE, policiesByCode.keySet());
        }
    }

    public RefundPolicy resolve(String policyCode) {
        RefundPolicy policy = policiesByCode.get(policyCode);
        if (policy == null) {
            throw new UnknownRefundPolicyException(policyCode, knownCodes());
        }
        return policy;
    }

    public RefundPolicy resolveOrDefault(String policyCode) {
        return resolve(policyCode == null || policyCode.isBlank() ? DEFAULT_CODE : policyCode);
    }

    public Set<String> knownCodes() {
        return policiesByCode.keySet();
    }
}
