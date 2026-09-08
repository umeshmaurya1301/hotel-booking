package com.umesh.hotelbooking.web;

/**
 * Operation discriminator stamped onto audit, ledger and idempotency records (design doc
 * 11.3), one constant per endpoint.
 *
 * <p>Derived server-side by {@link ApiTypeInterceptor} from the {@link Api} annotation on the
 * matched handler method — never accepted as a client-supplied body field. In a REST API,
 * {@code POST /api/v1/user/bookings} already identifies the operation unambiguously; asking
 * the client to restate it in the body would duplicate information the route already carries.
 * The NPCI/UPI pattern this was modelled on needs an in-payload discriminator because those
 * messages share a single endpoint; that constraint does not exist here.
 */
public enum ApiType {
    ONBOARD_PROPERTY, UPDATE_PROPERTY, GET_PROPERTY,
    EXTEND_INVENTORY, REPRICE_INVENTORY, OVERRIDE_INVENTORY, VIEW_INVENTORY,
    RUN_SWEEPER, RUN_RECONCILIATION, RESOLVE_MANUAL_REVIEW, LIST_STUCK_PAYMENTS,
    MANUAL_REVERSAL, VIEW_LEDGER, GET_GUEST, REDACT_GUEST,
    SEARCH_PROPERTIES, CREATE_BOOKING, GET_BOOKING, PAY_BOOKING, GET_PAYMENT, CANCEL_BOOKING,
    PAYMENT_WEBHOOK,
    UNKNOWN
}
