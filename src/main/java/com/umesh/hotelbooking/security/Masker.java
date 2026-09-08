package com.umesh.hotelbooking.security;

/**
 * The one place every masking rule lives (design doc 12.6.4), called by both {@link
 * SensitiveSerializer} (API responses, stored idempotency replays) and {@code
 * PayloadRedactor} (provider payloads before they reach an audit row). Two independent
 * implementations of "mask a PAN" is how the two paths drift apart, so neither is allowed to
 * have its own copy of these rules.
 *
 * <p><b>Never throws.</b> This runs inside a Jackson serializer and inside a log appender —
 * an exception in either turns a redaction feature into an outage. Every mode falls back to
 * {@code "***"} on anything unexpected. Failing closed to full masking, never open to the raw
 * value, is the only safe direction when a masking rule itself misbehaves.
 *
 * <p>{@code null} in always yields {@code null} out. An empty string is not {@code null} —
 * masking it to {@code "***"} rather than {@code ""} keeps "absent" and "present but empty"
 * distinguishable downstream.
 */
public final class Masker {

    private static final String FULL_MASK = "***";

    /** {@code GuestRedactionService}'s tombstone marker. A field already overwritten with this
     * is not personal data any more — it is a status marker — so masking it further would
     * only make an already-erased field harder to read as confirmation that erasure worked. */
    public static final String TOMBSTONE = "[REDACTED]";

    private Masker() {
    }

    public static String mask(Masking mode, String value) {
        if (TOMBSTONE.equals(value)) {
            return value;
        }
        return switch (mode) {
            case FULL -> full(value);
            case PAN -> pan(value);
            case LAST4 -> last4(value);
            case EMAIL -> email(value);
            case PHONE -> phone(value);
            case NAME -> name(value);
        };
    }

    /** {@code 4111111111111111 -> 411111XXXXXX1111}: first six, {@code X} filler, last four.
     * Fewer than 11 digits has no meaningful middle to hide, so it falls back to {@link #full}. */
    public static String pan(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return FULL_MASK;
        }
        try {
            if (value.length() < 11) {
                return full(value);
            }
            String first6 = value.substring(0, 6);
            String last4 = value.substring(value.length() - 4);
            String filler = "X".repeat(value.length() - 10);
            return first6 + filler + last4;
        } catch (RuntimeException e) {
            return FULL_MASK;
        }
    }

    /** {@code 4111111111111111 -> ****1111}: a fixed four-star prefix, not proportional to length. */
    public static String last4(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return FULL_MASK;
        }
        try {
            if (value.length() < 4) {
                return full(value);
            }
            return "****" + value.substring(value.length() - 4);
        } catch (RuntimeException e) {
            return FULL_MASK;
        }
    }

    /** {@code asha@example.com -> a***@example.com}. No {@code @} means this is not shaped
     * like an email at all, so it falls back to {@link #full}. */
    public static String email(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return FULL_MASK;
        }
        try {
            int at = value.indexOf('@');
            if (at <= 0) {
                return full(value);
            }
            return value.charAt(0) + "***" + value.substring(at);
        } catch (RuntimeException e) {
            return FULL_MASK;
        }
    }

    /** {@code +919876543210 -> *****3210}: a fixed five-star prefix plus the last four
     * characters, not proportional to length. */
    public static String phone(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return FULL_MASK;
        }
        try {
            if (value.length() < 4) {
                return full(value);
            }
            return "*****" + value.substring(value.length() - 4);
        } catch (RuntimeException e) {
            return FULL_MASK;
        }
    }

    /** {@code Asha Menon -> A*** M****}: first letter of each whitespace-separated token,
     * followed by one {@code *} per remaining letter in that token. */
    public static String name(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return FULL_MASK;
        }
        try {
            String[] tokens = value.trim().split("\\s+");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < tokens.length; i++) {
                if (i > 0) {
                    result.append(' ');
                }
                String token = tokens[i];
                result.append(token.charAt(0));
                result.append("*".repeat(token.length() - 1));
            }
            return result.toString();
        } catch (RuntimeException e) {
            return FULL_MASK;
        }
    }

    /** Anything -> {@code ***}, unconditionally. */
    public static String full(String value) {
        if (value == null) {
            return null;
        }
        return FULL_MASK;
    }
}
