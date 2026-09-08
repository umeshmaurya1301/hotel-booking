/**
 * USER-category endpoints (design doc 11.4): guest-facing search, booking, payment and
 * cancellation actions under {@code /api/v1/user/**}, guarded by {@code @RequireRole(Role.USER)}.
 * A distinct package from {@code controller.admin} and {@code controller.webhook} so role
 * separation is structural, not merely a URL convention.
 */
package com.umesh.hotelbooking.controller.user;
