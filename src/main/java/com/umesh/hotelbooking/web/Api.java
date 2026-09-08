package com.umesh.hotelbooking.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a handler method's {@link ApiType}, read by {@link ApiTypeInterceptor}.
 *
 * <p>Deliberately not a URL-pattern-to-enum lookup table: that would be a second copy of the
 * routing table, drifting the moment someone edits a {@code @RequestMapping}. Declaring the
 * type on the method keeps it next to the route it describes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Api {
    ApiType value();
}
