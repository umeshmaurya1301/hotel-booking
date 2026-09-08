/**
 * HMAC signing and verification for the webhook envelope (design doc 12.1–12.3), used both
 * inbound (verifying a provider's callback) and outbound (signing our own merchant
 * notifications) — one signer, both directions.
 */
package com.umesh.hotelbooking.crypto;
