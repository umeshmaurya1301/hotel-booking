/**
 * Cross-cutting request/response handling shared by every controller package: the API
 * envelope advices ({@code RequestEnvelopeAdvice}, {@code ResponseEnvelopeAdvice}), and the
 * exception handlers that translate a thrown {@code DomainException} (or a webhook-specific
 * failure) into the structured error shape design doc 11 specifies.
 */
package com.umesh.hotelbooking.controller.advice;
