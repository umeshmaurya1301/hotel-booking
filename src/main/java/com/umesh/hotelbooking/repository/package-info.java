/**
 * The persistence <b>ports</b>: one {@code *Store} interface per aggregate, expressed purely in
 * domain terms.
 *
 * <p><b>Why these exist.</b> The brief requires persistence to sit "behind repository interfaces
 * so it could be swapped later". Extending {@code JpaRepository} directly satisfies that only in
 * letter: the interface is then a JPA interface, its query methods are JPA derived queries, and
 * its most important statements are JPQL. A caller depending on it depends on JPA, so swapping
 * the store means rewriting the interfaces, not just the implementations — the seam is in the
 * wrong place.
 *
 * <p>Nothing in this package imports {@code org.springframework.data}. A store is a plain Java
 * interface over domain types, so every collaborator that persists anything — services, search
 * filters, the seed loader — depends on a contract that a JDBC, document-store or in-memory
 * implementation could satisfy unchanged. The JPA implementations live in
 * {@link com.umesh.hotelbooking.repository.jpa} and are the only place a Spring Data type or a
 * line of JPQL appears.
 *
 * <p><b>The seam this buys.</b> {@code DailyInventoryStore#reserveUnits} is the system's
 * correctness mechanism (design doc 5.2.1). As a port it states the <em>contract</em> —
 * reserve-or-refuse, decided atomically, answered by a count — while the single conditional
 * {@code UPDATE} that currently implements it becomes one store's way of honouring that
 * contract. That is the difference between a guarantee the domain owns and a guarantee that
 * happens to be true of the ORM in use.
 *
 * <p><b>What is deliberately not done.</b> The stores return the {@code @Entity} types from
 * {@link com.umesh.hotelbooking.entity} rather than a separate set of persistence-free domain
 * objects mapped at the boundary. Entities here are already close to plain domain objects — the
 * state machines and invariants live on them, not in the services — so a parallel model plus
 * mappers would double the type count to remove an annotation. The honest statement of the
 * trade-off: the ports are store-agnostic, the types crossing them are still JPA-annotated, and
 * a genuinely non-relational implementation would need that second step. It is a step this
 * layer makes possible; it is not a step already taken.
 */
package com.umesh.hotelbooking.repository;
