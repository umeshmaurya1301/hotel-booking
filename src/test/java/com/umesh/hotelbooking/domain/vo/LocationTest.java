package com.umesh.hotelbooking.domain.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationTest {

    @Test
    void blankCityThrows() {
        assertThatThrownBy(() -> new Location(" ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void latitudeWithoutLongitudeThrows() {
        assertThatThrownBy(() -> new Location("Bengaluru", null, BigDecimal.valueOf(12.97), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outOfRangeLatitudeThrows() {
        assertThatThrownBy(() -> new Location(
                "Bengaluru", null, BigDecimal.valueOf(200), BigDecimal.valueOf(77.59)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalisedCityCollapsesCaseAndWhitespace() {
        Location location = new Location(" Bengaluru  City ", null, null, null);

        assertThat(location.normalisedCity()).isEqualTo("bengaluru city");
        assertThat(location.city()).isEqualTo(" Bengaluru  City ");
    }

    @Test
    void differingCasingNormalisesToTheSameCity() {
        Location a = new Location("Bengaluru", null, null, null);
        Location b = new Location("bengaluru", null, null, null);
        Location c = new Location(" Bengaluru ", null, null, null);

        assertThat(a.normalisedCity()).isEqualTo(b.normalisedCity()).isEqualTo(c.normalisedCity());
    }
}
