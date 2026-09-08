/**
 * Guest-facing property discovery (design doc 10): the filter chain behind {@code POST
 * /api/v1/user/properties/search}. Read-only, advisory (design doc 10.3) — nothing in this
 * package writes, locks or reserves anything. See {@link
 * com.umesh.hotelbooking.search.SearchFilter}'s Javadoc for the one deliberate deviation from
 * design doc 10.1's literal interface.
 */
package com.umesh.hotelbooking.search;
