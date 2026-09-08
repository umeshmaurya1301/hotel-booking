package com.umesh.hotelbooking.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Role} a controller (or one method of it) expects to be called by.
 *
 * <p>Authorisation is out of scope per the brief. This annotation and {@link RoleInterceptor}
 * demonstrate that role separation was designed for — three distinct URL spaces, packages and
 * annotations — without spending the time a real authorisation layer would cost. Enforcement
 * is trivially stubbed; see the interceptor's Javadoc for what that means in practice.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    Role value();
}
