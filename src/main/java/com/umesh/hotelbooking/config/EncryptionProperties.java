package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key material for field-level encryption at rest (design doc 12.6, Phase 10).
 *
 * <p>Like {@code WebhookProperties}' provider secrets, the committed value is a development
 * placeholder and nothing more: in production this comes from the environment or a secret
 * manager, never from committed YAML. The design document deliberately does not invent a
 * key-management story beyond externalised config, and neither does this.
 *
 * @param key Base64-encoded AES key — 16, 24 or 32 raw bytes (AES-128/192/256). Validated at
 *     startup by {@code FieldCipher}, so a malformed or wrong-length key fails the application
 *     immediately rather than at the first attempt to write a guest's name.
 */
@ConfigurationProperties("security.encryption")
public record EncryptionProperties(String key) {
}
