package com.umesh.hotelbooking.domain.vo;

import com.umesh.hotelbooking.domain.exception.CurrencyMismatchException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount in a specific ISO 4217 currency. Always non-negative in this domain: refund and
 * reversal direction is modelled by {@code LedgerEntry.direction} in a later phase, never by
 * the sign of the amount.
 *
 * <p>Never use {@code double} or {@code float} for money: binary floating point cannot
 * represent decimal currency amounts exactly, and rounding errors compound across a chain of
 * transactions. {@link BigDecimal} only.
 */
public record Money(BigDecimal amount, String currency) {

    private static final Pattern ISO_CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final int SCALE = 2;

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null || !ISO_CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException(
                    "currency must be a 3-letter uppercase ISO code, got: " + currency);
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, got: " + amount);
        }
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money inr(String amount) {
        return of(amount, "INR");
    }

    public static Money zeroInr() {
        return inr("0.00");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Throws if the result would be negative: negative money is meaningless in this domain,
     * so a subtraction that would underflow is a caller error, not a value to represent.
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "subtract would produce a negative amount: " + amount + " - " + other.amount);
        }
        return new Money(result, currency);
    }

    public Money multiply(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public Money percentage(BigDecimal percent) {
        BigDecimal result = amount.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_EVEN);
        return new Money(result, currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /**
     * Compares by normalised numeric value rather than {@link BigDecimal#equals}, which treats
     * {@code 10.00} and {@code 10.0} as unequal because their scales differ. The compact
     * constructor already normalises scale to 2, but this override makes the contract explicit
     * and immune to that invariant ever being weakened.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }
}
