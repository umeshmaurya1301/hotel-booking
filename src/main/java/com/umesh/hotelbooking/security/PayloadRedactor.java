package com.umesh.hotelbooking.security;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites a raw provider-payload JSON string before it reaches {@code webhook_event_log} or
 * {@code payment_status_check} (design doc 12.6.4) — the last line of defence before a
 * provider's own field names leak sensitive authentication or personal data into an audit
 * table.
 *
 * <p>Two behaviours, and the distinction between them is the whole of design doc 12.6.2:
 * sensitive authentication data ({@code cvv}, {@code pin}, track data, …) is <b>dropped
 * entirely</b>, never merely masked — a masked CVV in an audit row is still a CVV field that
 * should not exist there. Everything else in {@link #MASK_KEYS} is masked in place with
 * {@link Masker}, the same rules {@link SensitiveSerializer} uses, so the two paths cannot
 * drift apart.
 *
 * <p>Keys are matched case-insensitively and after stripping {@code _} and {@code -}, so
 * {@code card_number}, {@code cardNumber} and {@code CardNumber} all hit the same rule. This
 * is a deliberately conservative key-name matcher, not a content classifier — it will not
 * catch a PAN sitting under a key it does not recognise. {@link LogRedactionConverter} is the
 * value-shaped backstop for exactly that gap, the second of design doc 12.6.5's two layers.
 *
 * <p>Recurses into nested objects and arrays: provider payloads nest (e.g. {@code
 * {"instrument": {"pan": "..."}}}), and a top-level-only walk would miss them.
 *
 * <p>If the input is not parseable JSON, the whole thing returns {@code "***"} rather than
 * passing the unparseable string through — an unparseable payload has not earned the benefit
 * of the doubt, and letting it flow to an audit row unredacted is exactly the leak this class
 * exists to prevent.
 */
@Component
public class PayloadRedactor {

    private static final Set<String> DROP_KEYS = Set.of(
            "cvv", "cvv2", "cvc", "pin", "pinblock", "track1", "track2", "trackdata");

    private static final Map<String, Masking> MASK_KEYS = Map.ofEntries(
            Map.entry("pan", Masking.PAN),
            Map.entry("cardnumber", Masking.PAN),
            Map.entry("accountnumber", Masking.PAN),
            Map.entry("vpa", Masking.EMAIL),
            Map.entry("email", Masking.EMAIL),
            Map.entry("phone", Masking.PHONE),
            Map.entry("mobile", Masking.PHONE),
            Map.entry("contact", Masking.PHONE),
            Map.entry("name", Masking.NAME),
            Map.entry("billingname", Masking.NAME),
            Map.entry("customername", Masking.NAME));

    private static final String UNPARSEABLE = "***";

    private final ObjectMapper objectMapper;

    public PayloadRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String redact(String rawJson) {
        if (rawJson == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            return objectMapper.writeValueAsString(walk(root));
        } catch (JacksonException e) {
            return UNPARSEABLE;
        }
    }

    private JsonNode walk(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String normalized = normalize(entry.getKey());
                if (DROP_KEYS.contains(normalized)) {
                    continue;
                }
                Masking masking = MASK_KEYS.get(normalized);
                JsonNode value = entry.getValue();
                if (masking != null && value.isValueNode()) {
                    result.put(entry.getKey(), Masker.mask(masking, value.asString()));
                } else {
                    result.set(entry.getKey(), walk(value));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(element -> result.add(walk(element)));
            return result;
        }
        return node;
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
