package com.umesh.hotelbooking.security;

import com.umesh.hotelbooking.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * AES-GCM encryption for individual persisted fields (design doc 12.6, Phase 10) — encryption
 * <em>at rest</em> for the guest PII that {@code Guest} holds, so a stolen database file or
 * backup does not hand over names, emails, phone numbers and addresses in the clear.
 *
 * <p><b>This is a different concern from erasure, and does not replace it.</b> Design doc
 * 12.6.3's erasure story stands unchanged: {@code GuestRedactionService} still overwrites the
 * fields with a tombstone, which is real deletion of the plaintext. Crypto-shredding —
 * encrypting per-guest and discarding that guest's key to "erase" them — remains rejected for
 * the reason the README already gives: really deleting a genuinely isolated row is simpler and
 * a stronger guarantee than making a key disappear. What this class adds is protection against
 * a reader of the storage layer, which erasure never addressed.
 *
 * <p><b>GCM, not CBC or ECB.</b> GCM is authenticated: a tampered ciphertext fails to decrypt
 * rather than silently producing garbage plaintext, so a database row edited underneath the
 * application is detected instead of trusted. ECB would additionally leak equality (two guests
 * with the same city in their address producing identical blocks); a random 12-byte IV per
 * value means even two identical names encrypt differently, which is asserted by a test.
 *
 * <p><b>A {@link Cipher} instance is not thread-safe</b>, so one is created per call rather
 * than cached on this singleton bean — the same reasoning, and the same trap, that {@link
 * HmacSigner} already documents for {@code Mac}. Caching one here would produce intermittently
 * corrupt data under concurrent booking load and never fail in a single-threaded test.
 *
 * <p><b>Never logs plaintext, ciphertext or key material.</b> There is deliberately no logger
 * in this class at all.
 */
public class FieldCipher {

    /**
     * Marks a value this class produced, and carries a version so the algorithm or key regime
     * can change later without guessing at what an existing row contains. A stored value
     * <em>without</em> this prefix is treated as pre-encryption plaintext and returned
     * unchanged on read — which is what makes turning this on over an existing database a
     * rollout rather than a migration outage. Nothing writes unprefixed values.
     */
    private static final String PREFIX = "v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Set<Integer> VALID_KEY_LENGTHS = Set.of(16, 24, 32);

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(EncryptionProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.key()), "AES");
    }

    /**
     * Fails at bean-creation time — i.e. at startup — rather than at the first write. A
     * misconfigured key that only surfaces when a guest first supplies their name would be
     * discovered in production by a failed booking.
     */
    private static byte[] decodeKey(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "security.encryption.key is not set — guest PII cannot be encrypted at rest without it");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("security.encryption.key is not valid Base64", e);
        }
        if (!VALID_KEY_LENGTHS.contains(decoded.length)) {
            throw new IllegalStateException("security.encryption.key decodes to " + decoded.length
                    + " bytes; AES requires 16, 24 or 32");
        }
        return decoded;
    }

    /** @return {@code null} for {@code null} input — an absent field stays absent, and a
     * nullable column must not become a row of ciphertext meaning "nothing". */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // Deliberately does not include the value in the message.
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /**
     * @param stored a value produced by {@link #encrypt}, or plaintext written before this
     *     class existed (see {@link #PREFIX})
     * @throws IllegalStateException if the value carries this class's prefix but does not
     *     decrypt — a tampered or truncated ciphertext, or the wrong key. Failing loudly is
     *     the point of using an authenticated mode; returning something plausible would be
     *     worse than failing.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        byte[] iv = new byte[IV_BYTES];
        byte[] ciphertext = new byte[combined.length - IV_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_BYTES);
        System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field decryption failed: the value is not "
                    + "decryptable with the configured key (tampered, truncated, or encrypted "
                    + "under a different key)", e);
        }
    }
}
