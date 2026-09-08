package com.umesh.hotelbooking.web;

/**
 * The three API categories of design doc 11.4: operator, guest and payment-provider callers,
 * mirrored structurally by the {@code controller.admin} / {@code controller.user} / {@code
 * controller.webhook} packages.
 */
public enum Role {
    ADMIN, USER, SYSTEM
}
