package com.umesh.hotelbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring Framework 7's native resilience annotations and scheduling.
 *
 * <p>{@code @EnableResilientMethods} activates {@code org.springframework.resilience
 * .annotation.Retryable}, which ships with the framework itself and provides bounded retries
 * with exponential backoff, jitter and per-exception filtering. Worth noting for the payment
 * phase: this plus {@code @ConcurrencyLimit} may cover what Resilience4j's retry and
 * bulkhead were planned for, which matters because {@code resilience4j-spring-boot4} does
 * not exist on Maven Central yet.
 *
 * <p>{@code @EnableScheduling} drives the booking sweeper (design doc 4.4).
 */
@Configuration
@EnableResilientMethods
@EnableScheduling
public class ResilienceConfig {
}
