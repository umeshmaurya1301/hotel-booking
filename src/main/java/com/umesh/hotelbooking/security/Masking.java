package com.umesh.hotelbooking.security;

/**
 * The masking modes {@link Sensitive} can request (design doc 12.6.4). Deliberately closed —
 * do not add values without a corresponding rule in {@link Masker} and a row in its own
 * documentation table.
 */
public enum Masking {
    FULL, PAN, LAST4, EMAIL, PHONE, NAME
}
