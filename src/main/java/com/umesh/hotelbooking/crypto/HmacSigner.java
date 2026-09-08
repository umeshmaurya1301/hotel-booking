package com.umesh.hotelbooking.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signing and verification for the webhook envelope (design doc 12.1, 12.2). The
 * same signer is used inbound and outbound — one envelope, one signer, both directions.
 *
 * <p>A {@link Mac} instance is <b>not thread-safe</b>, so one is created fresh per call rather
 * than cached on this singleton bean. Caching one in a field here would, under concurrent
 * webhook delivery, produce intermittently wrong signatures — a genuinely horrible bug to
 * diagnose, since it would reproduce only under load and never in a single-threaded test.
 *
 * <p>{@link #verify} decodes both the expected and presented signatures to bytes before
 * comparing with {@link MessageDigest#isEqual}. Comparing the hex strings directly with
 * {@code isEqual} would still be constant-time, but decoding first makes that intent
 * unmistakable to a reader and sidesteps hex case differences for free.
 *
 * <p><b>Never logs the secret, the expected signature, or the presented signature.</b> An
 * expected-signature value sitting in a log line is a working forgery kit for anyone with log
 * access — only the provider code and the outcome (matched/mismatched) are ever logged, and
 * only by callers of this class, never here.
 */
@Component
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    /** @return {@code "sha256=<hex>"} */
    public String sign(byte[] body, String secret) {
        byte[] digest = hmac(body, secret);
        return PREFIX + HexFormat.of().formatHex(digest);
    }

    /**
     * @param presented the value from the inbound {@code X-Signature} header, expected to be
     *     {@code "sha256=<hex>"}. Malformed hex, a missing prefix, or any other parse failure
     *     returns {@code false} — it never throws.
     */
    public boolean verify(byte[] body, String secret, String presented) {
        if (presented == null) {
            return false;
        }
        String hex = presented.startsWith(PREFIX) ? presented.substring(PREFIX.length()) : presented;
        byte[] presentedBytes;
        try {
            presentedBytes = HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] expectedBytes = hmac(body, secret);
        return MessageDigest.isEqual(expectedBytes, presentedBytes);
    }

    private byte[] hmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
