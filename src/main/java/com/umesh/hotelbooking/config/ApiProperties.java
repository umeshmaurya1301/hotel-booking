package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API-layer settings (design doc 11).
 *
 * @param version contract version the server implements. Echoed in logs and compared against
 *     the envelope's {@code version} for observability only — a mismatch is recorded, never
 *     rejected. Rejecting on version skew would make every client upgrade a coordinated
 *     deploy, which is a stronger guarantee than this exercise needs.
 * @param correlationHeader response header the server-generated correlation id is returned on
 * @param roleHeader request header {@link com.umesh.hotelbooking.web.RoleInterceptor} reads
 */
@ConfigurationProperties("api")
public record ApiProperties(String version, String correlationHeader, String roleHeader) {

    public ApiProperties {
        version = version == null ? "v1" : version;
        correlationHeader = correlationHeader == null ? "X-Correlation-Id" : correlationHeader;
        roleHeader = roleHeader == null ? "X-Role" : roleHeader;
    }
}
