/**
 * SYSTEM-category endpoints (design doc 11.4): inbound, machine-to-machine callbacks from
 * payment providers under {@code /api/v1/webhooks/**}, authenticated by HMAC signature
 * ({@code PaymentWebhookController}, Phase 7) rather than a role header. A distinct package
 * from {@code controller.admin} and {@code controller.user} so the three-category separation
 * (admin / user / webhook) is structural, not merely a URL convention.
 */
package com.umesh.hotelbooking.controller.webhook;
