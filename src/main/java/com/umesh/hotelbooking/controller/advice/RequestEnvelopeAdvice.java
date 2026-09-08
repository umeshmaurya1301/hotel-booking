package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.dto.ApiRequest;
import com.umesh.hotelbooking.web.ApiContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * Copies {@link ApiRequest#msgId()} onto {@link ApiContext} as soon as the envelope has been
 * deserialized, so {@link GlobalExceptionHandler} can echo it even when the request goes on to
 * fail validation.
 *
 * <p>A filter cannot do this without buffering and re-parsing the whole body; the controller
 * cannot, because a validation failure never reaches it. {@code afterBodyRead} runs after
 * Jackson has produced the object but before Bean Validation rejects it, which is exactly the
 * window this needs.
 *
 * <p>If the request body is malformed JSON, {@code afterBodyRead} never runs at all and {@code
 * msgId} stays null in the resulting error response. That is correct, not a bug: the client
 * sent something that could not be parsed, so there is no key to echo.
 */
@ControllerAdvice(basePackages = "com.umesh.hotelbooking.controller")
public class RequestEnvelopeAdvice extends RequestBodyAdviceAdapter {

    private final ApiContext apiContext;

    public RequestEnvelopeAdvice(ApiContext apiContext) {
        this.apiContext = apiContext;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                             Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiRequest.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                 Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof ApiRequest<?> apiRequest) {
            apiContext.setMsgId(apiRequest.msgId());
        }
        return body;
    }
}
