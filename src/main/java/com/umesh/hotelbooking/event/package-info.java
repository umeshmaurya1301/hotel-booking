/**
 * Application events published in-process via Spring's {@code ApplicationEventPublisher}
 * (the Observer of design doc 14), keeping notification and audit concerns decoupled from
 * the flows that trigger them. These are the messages that would become Kafka topics if the
 * monolith were ever split (design doc 16).
 */
package com.umesh.hotelbooking.event;
