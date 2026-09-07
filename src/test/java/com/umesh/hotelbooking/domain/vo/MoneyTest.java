package com.umesh.hotelbooking.domain.vo;

import com.umesh.hotelbooking.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void tenPointZeroZeroEqualsTenPointZero() {
        // The BigDecimal.equals trap: BigDecimal("10.00").equals(BigDecimal("10.0")) is false
        // because scales differ. Money must not inherit that surprise.
        assertThat(Money.inr("10.00")).isEqualTo(Money.inr("10.0"));
    }

    @Test
    void scaleNormalisesToTwo() {
        Money money = Money.inr("10");

        assertThat(money.amount()).isEqualByComparingTo("10.00");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void halfEvenRoundingOnConstruction() {
        // 10.005 is exactly between 10.00 and 10.01; HALF_EVEN picks the even neighbour, 10.00.
        Money money = Money.inr("10.005");

        assertThat(money.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void negativeConstructionThrows() {
        assertThatThrownBy(() -> Money.inr("-1.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addAcrossDifferingCurrenciesThrows() {
        Money inr = Money.inr("10.00");
        Money usd = Money.of("10.00", "USD");

        assertThatThrownBy(() -> inr.add(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void subtractAcrossDifferingCurrenciesThrows() {
        Money inr = Money.inr("10.00");
        Money usd = Money.of("10.00", "USD");

        assertThatThrownBy(() -> inr.subtract(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void subtractYieldingNegativeThrows() {
        Money small = Money.inr("5.00");
        Money large = Money.inr("10.00");

        assertThatThrownBy(() -> small.subtract(large)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplyByThreeOnPerNightRateGivesExpectedTotal() {
        Money perNight = Money.inr("8000.00");

        assertThat(perNight.multiply(3)).isEqualTo(Money.inr("24000.00"));
    }

    @Test
    void percentageComputesShareOfAmount() {
        Money amount = Money.inr("200.00");

        assertThat(amount.percentage(BigDecimal.valueOf(50))).isEqualTo(Money.inr("100.00"));
    }

    @Test
    void isGreaterThanAndIsLessThanCompareByValue() {
        Money five = Money.inr("5.00");
        Money ten = Money.inr("10.00");

        assertThat(ten.isGreaterThan(five)).isTrue();
        assertThat(five.isLessThan(ten)).isTrue();
        assertThat(five.isGreaterThan(ten)).isFalse();
    }

    @Test
    void isZeroIsTrueOnlyForZeroAmount() {
        assertThat(Money.zeroInr().isZero()).isTrue();
        assertThat(Money.inr("0.01").isZero()).isFalse();
    }

    @Test
    void invalidCurrencyCodeThrows() {
        assertThatThrownBy(() -> Money.of("10.00", "inr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("10.00", "IN")).isInstanceOf(IllegalArgumentException.class);
    }
}
