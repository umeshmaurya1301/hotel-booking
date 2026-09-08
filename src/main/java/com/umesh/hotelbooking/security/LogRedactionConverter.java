package com.umesh.hotelbooking.security;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * The backstop layer of design doc 12.6.5: a Logback converter that regex-scans the
 * <em>rendered</em> log line for PAN-, email-, phone- and VPA-shaped substrings and masks
 * whatever it finds, independent of whether the code that logged the line knew to redact it.
 * Layer 1 ({@code @Sensitive}, {@link PayloadRedactor}) depends on developer discipline; this
 * exists because that discipline will occasionally be forgotten — a single hand-written
 * {@code log.debug} bypasses layer 1 entirely.
 *
 * <p>Order matters: VPA and email patterns run before the phone pattern, or a VPA like
 * {@code +919876543210@upi} gets half-mangled by the phone rule first (its digit run would be
 * consumed and masked before the {@code @upi} suffix is recognised as part of a VPA).
 *
 * <p>Deliberately no Luhn check on the PAN pattern. A backstop that only fires on
 * Luhn-valid numbers misses every non-Luhn-valid test PAN this codebase's own mock providers
 * emit, and the cost of over-masking some other long numeric id in a log line is nil compared
 * to the cost of missing a real one.
 *
 * <p><b>Never throws, and cheap.</b> This runs on every log line. Every {@link Pattern} is a
 * precompiled {@code static final} — never compiled inside {@link #convert}. The whole body is
 * guarded by a catch-all that returns the original, unmodified line on any failure: a logging
 * appender that throws takes the application down with it, which would be a far worse outcome
 * than an unmasked line slipping through this one converter.
 */
public class LogRedactionConverter extends ClassicConverter {

    private static final Pattern VPA = Pattern.compile("[\\w.-]+@(?:upi|ybl|okaxis|paytm|ok\\w+)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern PAN = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{8,14}\\d");

    @Override
    public String convert(ILoggingEvent event) {
        String line = event.getFormattedMessage();
        try {
            line = VPA.matcher(line).replaceAll(match -> Masker.email(match.group()));
            line = EMAIL.matcher(line).replaceAll(match -> Masker.email(match.group()));
            line = PAN.matcher(line).replaceAll(match -> Masker.pan(match.group()));
            line = PHONE.matcher(line).replaceAll(match -> Masker.phone(match.group()));
            return line;
        } catch (RuntimeException e) {
            return event.getFormattedMessage();
        }
    }
}
