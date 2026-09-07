package com.umesh.hotelbooking.domain.exception;

/**
 * Thrown when a binary {@code Money} operation (add, subtract, comparison) is attempted
 * between two amounts in different currencies. There is no implicit conversion in this
 * domain.
 */
public final class CurrencyMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(String currencyA, String currencyB) {
        super("CURRENCY_MISMATCH", "Cannot operate on differing currencies: " + currencyA + " and " + currencyB);
    }
}
