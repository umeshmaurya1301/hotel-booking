/**
 * ADMIN-category endpoints (design doc 11.4): operator-facing onboarding, inventory,
 * reconciliation, ledger and reversal actions under {@code /api/v1/admin/**}, guarded by
 * {@code @RequireRole(Role.ADMIN)}. A distinct package from {@code controller.user} and
 * {@code controller.webhook} so role separation is structural, not merely a URL convention.
 */
package com.umesh.hotelbooking.controller.admin;
