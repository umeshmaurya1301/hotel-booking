/**
 * Request and response payloads for the API layer. Controllers accept and return DTOs, never
 * entities directly, so the database shape (Long ids, JPA relationships) never leaks over
 * the wire.
 */
package com.umesh.hotelbooking.dto;
