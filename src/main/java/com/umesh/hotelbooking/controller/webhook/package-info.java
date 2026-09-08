/**
 * SYSTEM-category endpoints (design doc 11.4): inbound, machine-to-machine callbacks from
 * payment providers, authenticated by HMAC signature rather than a role header. Empty for now
 * — {@code PaymentWebhookController} and its HMAC verification arrive in Phase 7. The package
 * exists ahead of the controller it will hold so the three-category separation (admin / user /
 * webhook) is structural today, not merely a URL convention added later.
 */
package com.umesh.hotelbooking.controller.webhook;
