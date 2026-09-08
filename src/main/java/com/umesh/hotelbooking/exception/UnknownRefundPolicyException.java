package com.umesh.hotelbooking.exception;

import java.util.Collection;

public final class UnknownRefundPolicyException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownRefundPolicyException(String requestedCode, Collection<String> knownCodes) {
        super("UNKNOWN_REFUND_POLICY",
                "No refund policy with code " + requestedCode + "; known codes are " + knownCodes);
    }
}
