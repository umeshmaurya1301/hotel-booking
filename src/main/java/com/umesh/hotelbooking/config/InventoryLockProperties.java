package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the in-JVM inventory lock (design doc 5.2.4, layer 1).
 *
 * <p>This lock is a throughput optimisation, not the correctness mechanism. It exists to
 * serialise threads contending for the same room-night before they reach the database, so
 * they do not all issue an UPDATE that returns zero rows. Correctness comes from the atomic
 * conditional UPDATE and the check constraint underneath it, both of which hold across
 * instances, restarts and JVMs — which this lock does not.
 *
 * @param enabled turning this off must not permit an overbooking; a test asserts it does not
 * @param timeout {@code tryLock} bound. Never {@code lock()} without a timeout: an
 *     unbounded wait turns contention into a hung request (design doc 5.4).
 */
@ConfigurationProperties("inventory.lock")
public record InventoryLockProperties(boolean enabled, Duration timeout) {

    public InventoryLockProperties {
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
    }
}
