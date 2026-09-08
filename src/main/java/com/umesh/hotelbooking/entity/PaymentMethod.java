package com.umesh.hotelbooking.entity;

/**
 * How a guest is paying. Each mock provider declares which methods it handles (design doc
 * 6.1's {@code supports(method, bankCode)}).
 */
public enum PaymentMethod {
    CARD,
    UPI,
    WALLET
}
