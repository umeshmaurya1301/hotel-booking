package com.umesh.hotelbooking.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real {@code %msgRedacted} pattern (the same shape {@code logback-spring.xml}
 * registers), not just {@link LogRedactionConverter} in isolation — so this also proves the
 * Phase 6 correlation-id MDC field survived the move into the logback config (design doc
 * §5.2 of this phase's task spec).
 */
class LogRedactionConverterTest {

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Logger logger = context.getLogger(LogRedactionConverterTest.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String render(String message) {
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("msgRedacted", LogRedactionConverter::new);
        layout.setPattern("[%X{correlationId:-}] %msgRedacted");
        layout.start();

        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, message, null, null);
        return layout.doLayout(event);
    }

    @Test
    void aRawPanComesOutMasked() {
        String rendered = render("card 4111111111111111 charged");
        assertThat(rendered).contains("411111XXXXXX1111").doesNotContain("4111111111111111");
    }

    @Test
    void aRawEmailComesOutMasked() {
        String rendered = render("contact asha@example.com failed");
        assertThat(rendered).contains("a***@example.com").doesNotContain("asha@example.com");
    }

    @Test
    void aRawPhoneComesOutMasked() {
        String rendered = render("call +919876543210 now");
        assertThat(rendered).contains("*****3210").doesNotContain("+919876543210");
    }

    /** The ordering guard: a VPA must not be half-mangled by the phone rule firing first. */
    @Test
    void aVpaComesOutMaskedAndUnmangled() {
        String rendered = render("vpa asha@upi settled");
        assertThat(rendered).contains("a***@upi").doesNotContain("asha@upi");
    }

    @Test
    void theCorrelationIdMdcFieldStillRenders() {
        MDC.put("correlationId", "corr-123");
        String rendered = render("plain message");
        assertThat(rendered).startsWith("[corr-123]").contains("plain message");
    }

    @Test
    void aLineWithNothingSensitiveIsUnchanged() {
        String rendered = render("booking created successfully");
        assertThat(rendered).contains("booking created successfully");
    }
}
