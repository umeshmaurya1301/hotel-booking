package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Search settings (design doc 10.1).
 *
 * @param maxResults an unbounded search over a large city is a denial-of-service the client
 *     did not intend. No cursor paging — a real gap, stated rather than hidden (design doc
 *     10.3, the README section this phase adds).
 */
@ConfigurationProperties(prefix = "search")
public record SearchProperties(int maxResults) {

    public SearchProperties {
        if (maxResults <= 0) {
            maxResults = 50;
        }
    }
}
