package com.umesh.hotelbooking.config;

import com.umesh.hotelbooking.dto.ApiError;
import com.umesh.hotelbooking.web.RequireRole;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.List;

/**
 * OpenAPI/Swagger UI wiring (design doc 17, Phase 10). Served at {@code /swagger-ui.html} with
 * the raw document at {@code /v3/api-docs}.
 *
 * <p><b>Why this class exists at all, rather than just the dependency.</b> Out of the box,
 * springdoc documents {@code POST /api/v1/user/bookings} as returning a bare {@code
 * BookingResponse} — because that is genuinely what the controller method returns. It is not
 * what a client receives: {@link com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice}
 * wraps every {@code controller.admin} / {@code controller.user} return value in an {@code
 * ApiResponse} envelope (design doc 11.2) at a layer springdoc cannot see. A generated document
 * that omits that wrapper is not merely incomplete, it is wrong — a client generated from it
 * would look for {@code bookingUid} at the top level and never find it, since the real payload
 * is nested under {@code data}. Documenting the shape the wire actually carries is the whole
 * job here.
 *
 * <p><b>Why a customizer rather than annotating twenty controller methods.</b> Which endpoints
 * are enveloped is knowledge {@code ResponseEnvelopeAdvice} already owns, expressed there as
 * two base packages. Restating it as a per-method annotation would be a second copy of that
 * decision, free to drift from the first — the exact failure mode design doc 16.8 records for
 * a doc comment that stayed true-when-written and false thereafter. {@link #ENVELOPED_PACKAGES}
 * below is deliberately the same two package names that advice is scoped to, so a future
 * controller package is either enveloped in both places or neither.
 *
 * <p>{@code controller.webhook} is correctly left unwrapped, matching that path's real
 * behaviour: a provider's callback gets a bare {@code WebhookAck}, never our envelope
 * (design doc 12, and that advice's own scoping).
 */
@Configuration
public class OpenApiConfig {

    /** The shared envelope component every enveloped response {@code allOf}-references. */
    static final String ENVELOPE_SCHEMA = "ApiResponseEnvelope";

    /**
     * Exactly {@code ResponseEnvelopeAdvice}'s own {@code basePackages}. If one list changes,
     * the other must — see this class's Javadoc for why they are not merged into one constant:
     * {@code @RestControllerAdvice} needs compile-time constant strings in its own annotation,
     * so the coupling is stated here in prose rather than enforced by the compiler.
     */
    private static final List<String> ENVELOPED_PACKAGES = List.of(
            "com.umesh.hotelbooking.controller.admin",
            "com.umesh.hotelbooking.controller.user");

    @Bean
    public OpenAPI hotelBookingOpenApi(ApiProperties apiProperties) {
        return new OpenAPI().info(new Info()
                .title("Hotel Booking Platform API")
                .version(apiProperties.version())
                .description("""
                        Booking, payment, cancellation and search for a multi-property hotel platform.

                        Every state-changing `/api/v1/admin/**` and `/api/v1/user/**` request is wrapped \
                        in an `ApiRequest` envelope carrying `msgId` (the single idempotency key — \
                        design doc 8a), and every response from those paths is wrapped in an \
                        `ApiResponse` envelope. The `/api/v1/webhooks/**` path is deliberately \
                        different on both counts: it carries a provider's own `WebhookEnvelope`, is \
                        authenticated by HMAC signature rather than the role header, and its response \
                        is never enveloped by us.

                        Role separation is structural but its enforcement is stubbed (design doc \
                        11.4): a mismatched `X-Role` header is rejected, a missing one is currently \
                        let through.
                        """));
    }

    /**
     * Registers the envelope's own shape once, as a named component, so ~20 operations can
     * {@code allOf}-reference it instead of each inlining a copy of the same five fields.
     * {@code data} is deliberately not declared here — it is the one part that differs per
     * operation, and {@link #envelopeResponseCustomizer()} contributes it.
     */
    @Bean
    public OpenApiCustomizer envelopeComponentCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            Components components = openApi.getComponents();

            // ApiError is never a controller return type, so springdoc has no reason to have
            // discovered it — but it is what every FAILURE response actually carries, so the
            // document is incomplete without it. Resolved through Swagger's own converter
            // rather than hand-written, so its nested FieldError comes along correctly.
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .readAllAsResolvedSchema(ApiError.class);
            if (resolved != null && resolved.referencedSchemas != null) {
                resolved.referencedSchemas.forEach(components::addSchemas);
            }

            components.addSchemas(ENVELOPE_SCHEMA, new ObjectSchema()
                    .description("The response envelope every /api/v1/admin/** and /api/v1/user/** "
                            + "response is wrapped in (design doc 11.2). `data` and `error` are "
                            + "mutually exclusive.")
                    .addProperty("msgId", new StringSchema()
                            .description("Echoed from the request for client-side correlation. Null "
                                    + "only when the request body could not be parsed at all."))
                    .addProperty("correlationId", new StringSchema()
                            .description("Server-generated trace handle, always present. Also "
                                    + "returned as the X-Correlation-Id response header."))
                    .addProperty("status", new StringSchema()
                            ._enum(List.of("SUCCESS", "FAILURE", "PENDING"))
                            .description("PENDING is a first-class outcome, not a failure: a payment "
                                    + "whose gateway result was never observed reports PENDING rather "
                                    + "than guessing (design doc 7.2)."))
                    .addProperty("error", new Schema<>()
                            .$ref("#/components/schemas/ApiError")
                            .description("Present only on FAILURE."))
                    .addProperty("respondedAt", new StringSchema().format("date-time")));
        };
    }

    /**
     * Wraps each enveloped operation's 2xx response schema, and documents the {@code X-Role}
     * header that {@code RoleInterceptor} reads on every {@code /api/v1/**} route outside the
     * webhook path — without it, a reader of this document has no way to know the header exists.
     */
    @Bean
    public OperationCustomizer envelopeResponseCustomizer() {
        return (operation, handlerMethod) -> {
            if (!isEnveloped(handlerMethod.getBeanType().getPackageName())) {
                return operation;
            }
            documentRoleHeader(operation, handlerMethod.getBeanType());

            if (operation.getResponses() != null) {
                operation.getResponses().forEach((statusCode, response) -> {
                    if (statusCode.startsWith("2")) {
                        wrapContentSchemas(response.getContent());
                    }
                });
            }
            return operation;
        };
    }

    private static boolean isEnveloped(String packageName) {
        return ENVELOPED_PACKAGES.stream().anyMatch(packageName::startsWith);
    }

    private static void wrapContentSchemas(Content content) {
        if (content == null) {
            return;
        }
        content.values().forEach(mediaType -> {
            Schema<?> payload = mediaType.getSchema();
            // A composed schema is one this customizer already produced; wrapping it again
            // would nest an envelope inside an envelope.
            if (payload == null || payload instanceof ComposedSchema) {
                return;
            }
            mediaType.setSchema(new ComposedSchema()
                    .addAllOfItem(new Schema<>().$ref("#/components/schemas/" + ENVELOPE_SCHEMA))
                    .addAllOfItem(new ObjectSchema().addProperty("data", payload)));
        });
    }

    private static void documentRoleHeader(io.swagger.v3.oas.models.Operation operation, Class<?> controller) {
        RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(controller, RequireRole.class);
        if (requireRole == null) {
            return;
        }
        Parameter roleHeader = new HeaderParameter()
                .name("X-Role")
                .required(false)
                .description("Caller's role. Enforcement is stubbed (design doc 11.4): a value other "
                        + "than " + requireRole.value() + " is rejected, a missing header is currently "
                        + "let through.")
                .schema(new StringSchema()._enum(List.of(requireRole.value().name())));
        operation.addParametersItem(roleHeader);
    }
}
