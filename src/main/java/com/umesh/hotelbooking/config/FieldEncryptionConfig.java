package com.umesh.hotelbooking.config;

import com.umesh.hotelbooking.security.EncryptedLocalDateConverter;
import com.umesh.hotelbooking.security.EncryptedStringConverter;
import com.umesh.hotelbooking.security.FieldCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the field-encryption beans (design doc 12.6, Phase 10) in one place.
 *
 * <p><b>Why a configuration class rather than three {@code @Component}s.</b> {@code Guest}'s
 * PII columns carry {@code @Convert}, so Hibernate must be able to build {@code
 * EncryptedStringConverter} — with its {@link FieldCipher} dependency — before it can build the
 * {@code EntityManagerFactory} at all. That makes these beans a prerequisite of the persistence
 * layer itself, not merely of the code that reads a guest: without them, <em>every</em>
 * {@code @DataJpaTest} slice fails to start, including slices that never touch a {@code Guest}.
 * Component scanning covers the full application context but not a slice, so grouping them here
 * gives every slice a single {@code @Import(FieldEncryptionConfig.class)} instead of three
 * imports plus an {@code @EnableConfigurationProperties}.
 *
 * <p>A static holder would have avoided the imports entirely and was rejected: this project
 * states plainly that every singleton in it is a Spring-managed bean and never a hand-rolled
 * {@code getInstance()} (see the README's excluded-patterns note), and a mutable static holding
 * a cipher is exactly the shape that principle exists to keep out. The imports are the visible,
 * honest cost of encrypting a column that an entity mapping depends on.
 */
@Configuration
@EnableConfigurationProperties(EncryptionProperties.class)
public class FieldEncryptionConfig {

    @Bean
    public FieldCipher fieldCipher(EncryptionProperties properties) {
        return new FieldCipher(properties);
    }

    @Bean
    public EncryptedStringConverter encryptedStringConverter(FieldCipher fieldCipher) {
        return new EncryptedStringConverter(fieldCipher);
    }

    @Bean
    public EncryptedLocalDateConverter encryptedLocalDateConverter(FieldCipher fieldCipher) {
        return new EncryptedLocalDateConverter(fieldCipher);
    }
}
