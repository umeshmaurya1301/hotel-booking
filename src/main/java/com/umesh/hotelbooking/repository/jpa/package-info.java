/**
 * The JPA <b>adapters</b>: the only package in the application that names a Spring Data type or
 * contains a line of JPQL.
 *
 * <p>Each aggregate has two files here. {@code Jpa<X>Repository} is the Spring Data interface —
 * derived query methods, {@code @Query}, {@code @Modifying} — and is package-private in spirit:
 * nothing outside this package injects one. {@code Jpa<X>Store} is a {@code @Component}
 * implementing the matching port from {@link com.umesh.hotelbooking.repository} by delegating to
 * it.
 *
 * <p>The delegation is deliberately thin. An adapter that translated, cached or reordered would
 * be a second place where persistence behaviour is decided; keeping it to a forwarding call
 * means the query semantics stay in one readable place, and the cost of the extra layer is a
 * method call rather than a second thing to reason about. The two adapters that do more than
 * forward — {@link com.umesh.hotelbooking.repository.jpa.JpaPropertyStore} for its two-query
 * fetch strategy, and the inventory adapter's row-count contract — say so in their own Javadoc,
 * and both are doing exactly the work that should not be visible through a port.
 */
package com.umesh.hotelbooking.repository.jpa;
