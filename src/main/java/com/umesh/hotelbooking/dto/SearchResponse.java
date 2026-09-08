package com.umesh.hotelbooking.dto;

import java.util.List;

/**
 * @param truncated {@code true} when {@code search.max-results} clipped the sorted result
 *     list. Without this, a client cannot tell a complete result set from a clipped one — it
 *     would show "3 hotels in Mumbai" when there are sixty (task spec §4.2).
 */
public record SearchResponse(int resultCount, boolean truncated, List<PropertySearchResult> results) {
}
