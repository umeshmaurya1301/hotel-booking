/**
 * The payment gateway SPI: {@code PaymentGatewayProvider} and the mock implementations that
 * stand in for real banks, plus the router, circuit breaker and retrying client that sit in
 * front of them. Nothing outside this package calls a provider directly.
 */
package com.umesh.hotelbooking.gateway;
