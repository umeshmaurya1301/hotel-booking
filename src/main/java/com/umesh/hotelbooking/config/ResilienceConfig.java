package com.umesh.hotelbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring Framework 7's native resilience annotations, scheduling, and async execution.
 *
 * <p>{@code @EnableResilientMethods} activates {@code org.springframework.resilience
 * .annotation.Retryable}, which ships with the framework itself and provides bounded retries
 * with exponential backoff, jitter and per-exception filtering. Worth noting for the payment
 * phase: this plus {@code @ConcurrencyLimit} may cover what Resilience4j's retry and
 * bulkhead were planned for, which matters because {@code resilience4j-spring-boot4} does
 * not exist on Maven Central yet.
 *
 * <p>{@code @EnableScheduling} drives the booking sweeper (design doc 4.4).
 *
 * <p>{@code @EnableAsync} backs {@code WebhookDispatcher}'s {@code @Async} outbound delivery
 * (design doc 12.4, Phase 7 task spec §10) — a guest's booking response must not wait on a
 * merchant's endpoint. {@code spring.threads.virtual.enabled: true} in {@code application.yml}
 * is what makes Spring's default async executor virtual-thread-backed, so no separate
 * {@code Executor} bean is declared here.
 */
@Configuration
@EnableResilientMethods
@EnableScheduling
@EnableAsync
public class ResilienceConfig {
}
