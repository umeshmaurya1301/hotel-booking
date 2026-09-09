Hotel Booking Platform — Design Document
Rupeek SDE-3 Machine Coding Round · Question A Author: Umesh Maurya Stack: Java 21 · Spring Boot 4.1.x · H2 (MySQL compatibility mode) · Gradle

0. Reading Guide
This document is the design record for the project. It is deliberately explicit about what is built, what is not built, and why — the decision not to build something is as much a part of the design as the code itself.
This is a modular monolith, not a set of microservices. It goes deliberately deep in one place (the payment-ambiguity path, Section 7) and stays shallow elsewhere; Section 16 records what was consciously left out, along with how each item would be approached if it were in scope.
The numbered sections here are what the code comments cite: a comment reading "design doc 5.2.4" points at Section 5.2.4 below.

0.1 A mid-course revision — entities replace the domain/entity split
Sections 2 and 3 below originally described a hexagonal design: a framework-free domain model (plain classes, typed value-object ids, repository port interfaces) kept deliberately separate from JPA persistence entities, connected by an explicit mapper. That was built for Phase 1 and then deliberately reworked, on explicit direction, into a single conventional entity layer before Phase 2 began. This document has been updated in place to describe the current (post-revision) design rather than keeping the superseded one as a historical artifact — a design record that documents an approach no longer in the code would mislead a reviewer more than it would inform one.
What changed, and why it is stated as a revision rather than quietly rewritten: the earlier design's core claim was that keeping the domain framework-free (Section 4's coding rule: no JPA/Spring annotations, no now(), typed ids instead of primitives) buys testability and a persistence-agnostic core, at the cost of a duplicate model and a mapping layer. That trade-off is real, but it is also more machinery than a single-database, single-team submission needs to demonstrate at this scope — and simpler, more conventional Spring code is easier for a reviewer to read quickly. The revision trades that testability/purity story for less code and a shape any Spring Boot developer recognises immediately:
Entities now carry their own JPA annotations and Lombok-generated boilerplate directly (Section 2.3). There is no separate domain model and no mapper.
Every top-level aggregate has two ids: a Long id (@GeneratedValue(IDENTITY), the database primary key, never exposed) and a separate UUID "*Uid" column, assigned in @PrePersist, which is what every lookup and API actually uses (Section 3.2). This is the direct answer to a Long-primary-key's biggest weakness in a public API: sequential ids leak row counts and are guessable; the UUID never does.
Value objects (DateRange, Money, UnitCount, GuestCount, Location) and typed identifiers (BookingId, PaymentId, MsgId, …) are gone. Their validation either moved onto the entity directly (Booking.nights(), Booking.validateDateRange()) or became a Bean Validation annotation (@NotNull, @Min, @Max). The one deliberate capability loss worth naming: Money's compile-time currency-mismatch protection has no replacement — amount and currency are now plain BigDecimal/String fields with no guard against a cross-currency arithmetic bug. Accepted at this scope; would need revisiting if multi-currency were ever real.
Repository port interfaces are gone. repository.* is now just Spring Data JpaRepository<Entity, Long> interfaces — the Repository pattern is still present (Section 14), just auto-implemented rather than hand-wired through a port and an adapter.
BookingStateMachine is unchanged in logic (same total transition table, same terminal states, same same-state-is-a-no-op rule) but is now a stateless static utility rather than an injected component, since the port/adapter split was the only reason it needed to be instantiated.
Every other section of this document — inventory model, concurrency mechanism, payment SPI, resilience, idempotency, refunds/ledger, webhooks, persistence schema, testing strategy, phased plan — describes mechanisms and guarantees that do not depend on which of the two shapes holds the data, and are updated below only where they specifically referenced a now-removed type (InventoryKey, PropertyClock, Money, a typed id).

1. Problem Choice and Rationale
Chosen: Question A — Hotel Booking.
Deciding factor
Why hotel wins
Inventory is discrete and constrainable
(room_type_id, stay_date) is a natural uniqueness key. Enables a DB-level overbooking guard.
Locking demonstration
Lock exactly the room-nights being booked — precise, testable, explainable.
Concurrency test quality
N threads racing the last unit on one date; exactly one wins. Unambiguous.
Structural modelling requirement
The brief explicitly asks that a single property be a natural special case of the multi-property structure. Real modelling work, HIGH-weight.
Time risk
Bounded. Maid's recurring-occurrence expansion and partial-series cancellation is the single largest time sink in either brief.

What was given up: the maid brief's recurring bookings map more closely to mandate/autopay flows, which is the more interesting payments problem. That loss is partly recovered here — HMAC webhook verification, idempotency, reversals and the payment-ambiguity state are domain-agnostic and all appear in this design.
Why maid's inventory model is weaker for this purpose: a maid is a single resource with an interval-overlap problem. "No overlapping time ranges" cannot be expressed as a unique index in MySQL or H2 (exclusion constraints are a PostgreSQL feature). The DB-level correctness guard — the strongest persistence argument in this design — would not exist.

2. Architecture
2.1 Shape
Modular monolith, layered by responsibility (persistence, orchestration, HTTP), not by framework-independence.
Rationale: booking, inventory reservation and payment state must move together transactionally. Splitting them into services would manufacture a distributed-transaction problem that does not exist at this scope. The seams are drawn so a future split is mechanical — see Section 16.
2.2 Dependency direction
controller  ──►  service  ──►  repository  ──►  entity

entity is the persisted model: JPA-annotated directly, no separate domain representation.
repository is Spring Data JPA — an interface per entity, no hand-written implementation.
controller depends on dto and service, never on repository or entity directly, so the database shape never crosses the HTTP boundary.
There is deliberately no ports-and-adapters layer here (see 0.1). The dependency direction above is about keeping the HTTP contract (dto) decoupled from the storage contract (entity) — the one seam this design actually needs — not about isolating business logic from the framework.
2.3 Package structure
com.umesh.hotelbooking
│
├── entity               JPA entities — the persisted model. Each aggregate carries a Long
│                        id (the database primary key, @GeneratedValue(IDENTITY), never
│                        exposed) and a second UUID "*Uid" column, assigned in @PrePersist,
│                        which every lookup and API actually uses (see 0.1, 3.2).
│                        Owner, PropertyGroup, Property, RoomType, DailyInventory,
│                        Booking, BookingLineItem, BookingState, BookingStateMachine,
│                        Payment, Refund, Reversal, LedgerEntry, Guest
│
├── dto                  Request/response payloads for the API layer. Controllers accept
│                        and return these, never entities, so Long ids and JPA
│                        relationships never leak over the wire.
│                        ApiRequest, ApiResponse, ApiError, ResponseStatus, per-endpoint
│                        request/response records
│
├── repository           One Spring Data JpaRepository<Entity, Long> interface per
│                        aggregate, plus a lookup by that aggregate's business uid.
│                        BookingRepository, PropertyRepository, RoomTypeRepository,
│                        DailyInventoryRepository, GuestRepository, PaymentRepository,
│                        LedgerEntryRepository
│
├── service              Use-case orchestration: coordinates entities, repositories and
│                        transactions. Where PricingStrategy, RefundPolicy,
│                        PaymentGatewayProvider and the reservation/reconciliation logic
│                        live.
│                        PropertyOnboardingService, InventoryMaterializer,
│                        PropertySearchService, SearchFilterChain, BookingService,
│                        InventoryReservationService, PaymentService,
│                        PaymentGatewayRouter, IdempotencyService,
│                        ReconciliationService, CancellationService, RefundService,
│                        ReversalService, LedgerService, NotificationDispatcher
│
├── controller
│   ├── admin            PropertyAdminController, InventoryAdminController,
│   │                    LedgerAdminController, ReversalAdminController
│   ├── user             SearchController, BookingController,
│   │                    PaymentController, CancellationController
│   ├── webhook          PaymentWebhookController
│   └── advice           GlobalExceptionHandler, ResponseEnvelopeAdvice
│
├── exception            DomainException hierarchy, each carrying a stable error code
│
├── event                In-process application events (the Observer of section 14),
│                        published via ApplicationEventPublisher and consumed with
│                        @TransactionalEventListener(AFTER_COMMIT) so nothing is
│                        announced for a transaction that then rolls back.
│                        PropertyOnboardedEvent, BookingConfirmedEvent,
│                        BookingCancelledEvent, PaymentSettledEvent
│
├── gateway/crypto/webhook/notification   Mock payment gateways, HMAC signing/
│                        verification, webhook event log, outbound webhook delivery,
│                        mock email/SMS channels. (Package boundaries to be finalised
│                        when Phase 4/7 build these out — flat siblings of the above,
│                        not nested under an "infrastructure" umbrella.)
│
└── config               ResilienceConfig, VirtualThreadConfig, SeedDataLoader, a
                         java.time.Clock bean (see 4.5)

On the flattened shape: earlier revisions of this document nested everything under domain / application / infrastructure / api specifically to keep the domain package framework-free and to make the ports-and-adapters boundary visible in the folder structure. With that boundary gone (0.1), the nesting no longer signals anything — entity, repository, service and controller are peers in a conventional layered Spring Boot application, and the flat structure says so plainly.

3. Domain Model
3.1 Ownership hierarchy
Owner (account)
 └── PropertyGroup            e.g. "Taj Group"
      └── Property            e.g. "Taj MG Road, Bengaluru"
           └── RoomType       e.g. "Deluxe King", 10 units, ₹8000/night
                └── DailyInventory    one row per calendar night

Single-property owners take the identical path. They are assigned a PropertyGroup containing exactly one Property. No branching, no if (isChain) anywhere in the codebase. This is the direct answer to the brief's requirement.
On the Composite pattern: this is a uniform one-to-many hierarchy, not textbook Composite. Composite exists to allow arbitrary nesting of node and leaf behind one interface — a hotel does not contain hotels, so that nesting requirement is absent. Using full Composite here would be unjustifiable decoration. This distinction is stated explicitly because it is likely to be probed.
3.2 Identity: the two-id convention (revised — see 0.1)
There are no value objects and no typed identifiers in the current design. Every top-level entity instead carries two ids:
Id
Type
Purpose
id
Long, @GeneratedValue(IDENTITY)
The database primary key. Internal only — never serialised into a response, never accepted from a request, never logged. This is what JPA relationships (@ManyToOne, @JoinColumn) reference internally.
"*Uid" (e.g. bookingUid, propertyUid)
String (UUID), assigned in @PrePersist, unique + updatable = false
What every API, log line and repository lookup actually uses. Generated once, at persist time, and never changes.

Why two ids rather than one: a bare auto-increment Long exposed over an API leaks information (row counts, growth rate, existence-by-guessing — request id 41982, then 41983) and cannot be generated before the row is inserted, which matters once idempotent retry needs a stable reference id before the first persist attempt. A UUID solves both, but using it as the actual database primary key costs index locality and row size versus a Long — so the Long stays as the storage-internal PK and the UUID becomes the boundary-facing identity. This is the standard "surrogate key + natural/business key" pattern, applied uniformly.
Two deliberate exceptions:
BookingLineItem has only a Long id. Nothing ever looks up a line item on its own — it is always reached through its parent Booking — so a second id would be a synthetic identifier with no caller.
DailyInventory has only a Long id, no UUID. Its real, externally-meaningful key is the (roomTypeId, stayDate) pair, enforced as a unique constraint — inventing a UUID here would be a business key nothing would ever use to look it up.
What this replaces: the value objects that previously lived here (DateRange, Money, UnitCount, GuestCount, Location, and the typed id wrappers BookingId/PaymentId/MsgId/etc.) validated themselves at construction and made an invalid instance unconstructable. That guarantee is gone. In its place:
DateRange's behaviour (checkout-exclusive night expansion, the 30-night cap) now lives directly on Booking as nights(), nightCount() and validateDateRange(), the latter enforced via @PrePersist/@PreUpdate rather than a constructor.
Simple range/positivity rules (units 1–10, adults ≥ 1, children ≥ 0, checkIn/checkOut not null) are Bean Validation annotations (@Min, @Max, @NotNull) on the entity fields — enforced once a controller validates an incoming DTO with @Valid, not at object-construction time. A Booking built via its Lombok builder with invalid field values is constructable; it simply fails validation before use.
Money's type-safe currency handling has no replacement. amount and currency are plain BigDecimal/String fields with no compile-time or runtime guard against mixing currencies in arithmetic. Stated plainly rather than left for a reviewer to discover: this is a real capability loss versus the value-object version, accepted because the system is single-currency (INR) at this scope.
Booking.nights() (formerly DateRange.nights()) remains the single place a date range becomes discrete nights, used by both inventory materialisation (Section 4) and reservation (Section 5). That property survived the revision even though the type it lived on did not.
3.3 Booking state machine
                   ┌──────────────────────────────────┐
                    │                                  ▼
  CREATED ──► PENDING_PAYMENT ──► CONFIRMED ──► CANCELLED
                    │                  │
                    │                  └──────► COMPLETED   (after checkout)
                    │
                    ├──► PAYMENT_UNKNOWN ──► CONFIRMED
                    │            │                (late success, still valid)
                    │            ├──► REVERSED
                    │            │      (late success, hold already expired)
                    │            ├──► PAYMENT_FAILED
                    │            └──► MANUAL_REVIEW ──► CONFIRMED | REVERSED
                    │                   (status checks exhausted)  | PAYMENT_FAILED
                    │
                    ├──► PAYMENT_FAILED ──► EXPIRED
                    │
                    └──► EXPIRED
                             (hold lapsed before payment)

Implemented as an explicit transition table, not scattered if checks:
private static final Map<BookingState, Set<BookingState>> ALLOWED = Map.of(
    CREATED,          EnumSet.of(PENDING_PAYMENT, EXPIRED),
    PENDING_PAYMENT,  EnumSet.of(CONFIRMED, PAYMENT_FAILED, PAYMENT_UNKNOWN, EXPIRED),
    PAYMENT_UNKNOWN,  EnumSet.of(CONFIRMED, PAYMENT_FAILED, REVERSED, MANUAL_REVIEW),
    MANUAL_REVIEW,    EnumSet.of(CONFIRMED, PAYMENT_FAILED, REVERSED),
    CONFIRMED,        EnumSet.of(CANCELLED, COMPLETED),
    PAYMENT_FAILED,   EnumSet.of(EXPIRED),
    CANCELLED,        EnumSet.noneOf(BookingState.class),   // terminal
    COMPLETED,        EnumSet.noneOf(BookingState.class),   // terminal
    EXPIRED,          EnumSet.noneOf(BookingState.class),   // terminal
    REVERSED,         EnumSet.noneOf(BookingState.class)    // terminal
);

Illegal transitions throw InvalidStateTransitionException. Idempotent re-application of the current state is a no-op, not an error — required so a duplicated webhook does not fail loudly (Section 8c).
BookingStateMachine itself is a stateless utility with static methods (canTransition, assertCanTransition, isTerminal, allowedFrom), not an injected Spring bean — the port/adapter split that used to justify making it an instantiable, injectable component is gone (0.1), and a pure transition-table lookup has no state to inject in the first place.
Inventory is released on: CANCELLED, EXPIRED, PAYMENT_FAILED, REVERSED.
PAYMENT_UNKNOWN is the design centrepiece. When the circuit breaker is open or the gateway times out, the payment did not fail — the outcome is unobserved. Modelling this as a distinct state rather than collapsing it into PAYMENT_FAILED is what makes reconciliation and reversal coherent rather than hypothetical.
3.4 Booking Composition — Multi-Unit and Per-Night Pricing
A booking is not one room for one flat price. It is N units across M nights, each night priced independently.
Booking                                 (JPA entity — see 3.2 for the id/bookingUid pair)
  id, bookingUid
  guestId, propertyId, roomTypeId       Long — reference only, no @ManyToOne to Guest (12.6.3)
  checkIn, checkOut                     LocalDate       property-local (see 4.5)
  units                                 int             e.g. 2 Deluxe rooms; @Min(1) @Max(10)
  adults, children                      int             @Min(1) on adults
  holdExpiresAt                         Instant         see 4.4
  totalAmount, currency                 BigDecimal, String   = sum(lineItems); see 3.2 on losing Money
  lineItems                             List<BookingLineItem>   @OneToMany, cascade ALL
  state                                 BookingState
  version                               Long            @Version — JPA-managed optimistic lock,
                                                         no hand-rolled increment/compare

BookingLineItem                         one per night (JPA entity — Long id only, see 3.2)
  booking           @ManyToOne
  stayDate          LocalDate
  units             int
  pricePerUnit      BigDecimal        SNAPSHOT at booking time
  lineTotal         BigDecimal        = pricePerUnit × units

3.4.1 Why line items rather than a computed total
Price is captured at booking time, never recalculated. A later rate change on daily_inventory must not alter the amount owed on an existing booking. Without line items the only options are recomputing (wrong) or storing a bare total (unauditable — no answer to "why is this ₹34,000?").
Line items also make partial refunds tractable: a refund policy that returns unused nights has per-night amounts to work with rather than a percentage of a lump sum.
3.4.2 Multi-unit consequences
Concern
Handling
Availability
Every night needs booked_units + units <= total_units, not merely < total_units
Guest capacity
units × roomType.maxGuests >= (adults + children) — validated at booking
Inventory decrement
+units per night, not +1
Release on cancel/expire
-units per night
Concurrency
Strictly more interesting than single-unit. With 3 units free and two concurrent 2-unit requests, exactly one must win — a partial allocation would be a correctness failure that a single-unit test cannot detect.

Multi-unit is modelled rather than assumed away because "one booking = one room" is not how hotel platforms work, and silently implying it reads as unnoticed rather than decided.
3.5 Payment and refund lifecycles are independent
Payment:  INITIATED → PROCESSING → SETTLED | FAILED | UNKNOWN → MANUAL_REVIEW
Refund:   REQUESTED → PROCESSING → COMPLETED | FAILED
Reversal: INITIATED → COMPLETED | FAILED

Refund status is not collapsed into booking status. A booking can be CANCELLED while its refund is still PROCESSING. Conflating them is a common modelling error and loses information the business actually needs.

4. Inventory Model
4.1 Chosen representation: discrete per-night rows
room_type_id
stay_date
total_units
booked_units
price_per_unit
101
2026-09-10
10
3
8000
101
2026-09-11
10
7
8000
101
2026-09-12
10
10
12000

Booking 10–13 Sept touches three rows (checkout day is not a night).
Two things live on this row deliberately:
price_per_unit — the rate for that specific night. This is what makes per-night pricing (weekend, seasonal, event surge) expressible at all, and it is where PricingStrategy writes. A single basePricePerNight on RoomType cannot express it, and a pricing strategy with nowhere to attach is decoration.
No version column. Optimistic locking is not used on this table — see 5.2. A version column that nothing reads is dead weight and invites the question of why it is there.
Alternative rejected: storing bookings as date ranges and computing availability via overlap queries. Fewer rows and less setup, but it costs three things this design depends on:
No natural row to lock — you would lock the whole room type or the whole table.
No @Version to check on a row that does not exist yet, so optimistic locking is awkward.
No expressible unique constraint — "no overlapping ranges" is not a unique index in MySQL or H2. The DB-level correctness guard disappears entirely.
Point 3 is the dealbreaker.
4.2 Materialisation strategy: eager, bounded horizon
Inventory rows are generated at onboarding time for a configurable window (inventory.horizon-days: 90).
Strategy
Trade-off
Eager (chosen)
Availability check is a plain indexed read. Rows always exist, so locking is uniform. Concurrency test is trivial to set up. Cost: 10 room types × 90 days = 900 rows per property, plus a job to roll the window forward.
Lazy (create on first booking)
No wasted rows, but every read must handle "row absent = available", and create-on-first-book is itself a check-then-act race requiring upsert semantics.
Hybrid
Correct for production, most code.

Chosen knowingly. README states that production would use a rolling-window job plus lazy creation beyond the horizon, and that the lazy path introduces an insert race requiring INSERT ... ON DUPLICATE KEY semantics.
4.2.1 Where PricingStrategy attaches
public interface PricingStrategy {
    BigDecimal priceFor(RoomType roomType, LocalDate stayDate);   // in the property's currency
    String strategyCode();
}

Implementations: FlatRatePricing, WeekendSurgePricing (configurable multiplier on Fri/Sat), SeasonalPricing (date-band overrides).
Applied at materialisation time, writing price_per_unit onto each night. Consequences:
Booking reads a stored price — no strategy evaluation on the hot path.
An admin can override a single night's rate without touching strategy code.
A strategy change affects future materialisation only; existing bookings hold their snapshot (3.4.1) and existing inventory rows keep their price until explicitly re-priced.
POST /api/v1/admin/inventory/reprice re-runs a strategy over a date range. That endpoint is what makes the strategy demonstrably pluggable rather than a one-shot at onboarding.
4.3 Onboarding flow
POST /api/v1/admin/properties
  1. Validate         name non-blank, starRating 1..5, totalUnits > 0,
                      basePrice > 0, maxGuests > 0, city non-blank
  2. Resolve group    groupId present ? attach : create single-property group
  3. Persist          Property + RoomTypes
  4. Materialise      for each RoomType, for each date in [today_local, +horizon):
                      price = pricingStrategy.priceFor(roomType, date)
                      insert DailyInventory(roomTypeId, date, totalUnits, 0, price)
  5. Publish          PropertyOnboardedEvent

4.4 Hold Expiry — the Ordinary Abandonment Case
Distinct from the stuck-payment expiry of 7.6.1. This is the common case: a guest creates a booking and never pays. Without it, inventory leaks permanently on every abandoned booking.
Booking.holdExpiresAt = createdAt + booking.hold-ttl (default 15 minutes, config).
BookingSweeper runs on a fixed schedule and handles two transitions:
CREATED | PENDING_PAYMENT  where holdExpiresAt < now
    → EXPIRED ; release units on every night ; publish BookingExpiredEvent

CONFIRMED  where dateRange.checkOut < today_local(property)
    → COMPLETED

The second branch is why it exists at all: CONFIRMED → COMPLETED is in the transition table of 3.3 and would otherwise be unreachable. An unreachable state in a state machine is a defect, not an unused feature.
Race to be handled: the sweeper can fire while a payment is in flight. Expiry acquires the booking's optimistic lock and re-validates state; a payment that settles first wins and the sweep is a no-op. A payment that settles after expiry lands on the LATE_SUCCESS_ON_EXPIRED_BOOKING reversal path (9.2) — the same machinery, reused.
4.5 Dates Are Property-Local
checkIn and checkOut are local dates at the property, not instants and not server-local dates.
Trap
Handling
LocalDate.now() on a UTC server is the wrong day for a Bengaluru property near midnight
Property.zoneId; today_local(property) = LocalDate.now(clock.withZone(property.zoneId))
Guest in another timezone books "tonight"
Resolved against property-local date, which is what a hotel night actually means
Hold expiry and sweeper comparisons
holdExpiresAt is an Instant; checkout comparison uses property-local date
Test determinism
A single java.time.Clock @Bean (config.ClockConfig) is injected wherever "now" is needed; tests substitute a fixed Clock. No bare Instant.now() or LocalDate.now() in service-layer code.

A stay date is a calendar concept, not a timestamp — a hotel night is "the night of the 14th" regardless of the observer's timezone. Storing it as an instant would be the modelling error.
Revised (see 0.1): the earlier design wrapped this in a dedicated PropertyClock port (with a SystemPropertyClock adapter and a FixedPropertyClock test double) specifically so the framework-free domain package could resolve "today" without importing java.time.Clock's Spring-managed lifecycle. With that boundary gone, the extra interface has no remaining job — services now inject java.time.Clock directly, and tests substitute a fixed Clock bean or construct one inline. Booking.onCreate() is the one place that still calls Instant.now() directly rather than taking a Clock, because it is a JPA lifecycle callback with no constructor-injection point; this is a stated, narrow exception, not a return to bare now() calls throughout the codebase.

5. Concurrency and Thread Safety
5.1 The actual problem
Booking is a check-then-act sequence: read availability → reserve. Two threads can both pass the check and both reserve.
A thread-safe collection does not fix this. ConcurrentHashMap makes individual operations atomic; it does nothing for an invariant spanning two operations. Correctness requires either an atomic compound operation, a lock held across both steps, or DB-level serialisation. This design uses all three in layers.
5.2 Reservation Is a Single Atomic Statement
5.2.1 The mechanism
Reservation is one conditional UPDATE per night — a compare-and-set at the database, with no read-then-write window:
UPDATE daily_inventory
   SET booked_units = booked_units + :units
 WHERE room_type_id  = :roomTypeId
   AND stay_date     = :stayDate
   AND booked_units + :units <= total_units

rowsAffected = 1 → the units are reserved. rowsAffected = 0 → insufficient availability; throw InventoryUnavailableException.
The check-then-act problem does not arise, because there is no separate check. The predicate and the mutation are the same statement, evaluated under the row lock the database takes for the write. This is the correctness mechanism.
5.2.2 Why this over read → pessimistic lock → write


Atomic conditional UPDATE (chosen)
SELECT ... FOR UPDATE then write
Round trips per night
1
2
Lock hold duration
Statement duration
Read → decide → write, all under lock
Read-then-write window
None
Exists; correctness depends on the lock covering it
Version column needed
No
No (but often added anyway, then unused)
Failure signal
rowsAffected = 0
Application comparison

Fewer moving parts, shorter locks, and the invariant is expressed in the same statement that could violate it.
5.2.3 Multi-night is all-or-nothing
A 3-night booking issues three UPDATEs inside one transaction:
@Transactional
reserve(roomTypeId, dateRange, units):
    for stayDate in dateRange.nights().sorted():        // ordering matters — 5.3
        rows = conditionalIncrement(roomTypeId, stayDate, units)
        if rows == 0:
            throw InventoryUnavailableException(stayDate)   // rolls back earlier nights

Rollback handles partial allocation. There is no compensating-decrement code, because the transaction never commits a partial reservation. A booking that cannot get every night gets none — partial allocation would be a correctness failure, not a degraded success.
5.2.4 Layered defence
Layer
Mechanism
Role
1. In-JVM lock (optional fast path)
ConcurrentHashMap<LockKey, ReentrantLock> — LockKey(roomTypeId, stayDate) a small
service-private record, not a shared domain type (see 3.2 on the removal of InventoryKey)
Serialises same-key contenders before they reach the DB, avoiding round trips that would return rowsAffected = 0. Not the correctness mechanism.
2. Atomic conditional UPDATE
5.2.1
The guarantee. Correct across instances, restarts and JVMs.
3. Check constraint
CHECK (booked_units <= total_units)
Backstop. Even a hand-written query or a future code path cannot overbook.

Honest note on layer 1: with the atomic UPDATE in place, the in-JVM lock is a throughput optimisation, not a correctness requirement. It is retained because it cheaply reduces failed DB round trips under contention on a popular room-night, and because it bounds how many threads can be mid-transaction on the same key. It could be removed without affecting correctness, and the README says so. Presenting it as the safety mechanism would be overstating it.
Position worth stating plainly: locks here are for efficiency; the conditional statement and the constraint are for correctness. A lock is code that can be wrong. A CHECK constraint cannot be bypassed by application logic.
5.3 Deadlock Prevention — Still Load-Bearing
The atomic UPDATE does not remove the deadlock risk; it moves it into the database. Each UPDATE holds a row lock until the transaction commits, so a multi-night booking holds N row locks simultaneously:
Thread A books 10–12: holds lock on 10th, requests 11th
Thread B books 11–13: holds lock on 11th, requests 10th   → circular wait

On MySQL this surfaces as ERROR 1213 Deadlock found; on H2 as a lock-timeout exception. Either way one transaction is sacrificed — and the failure is load-dependent, so it will not show up in single-threaded testing.
Mechanism: total ordering on lock acquisition. All nights are locked in ascending (roomTypeId, stayDate) order, always. InventoryReservationService sorts booking.nights() before the loop and pairs each date with the room type id to get that ordering — a private Comparable LockKey record local to the service (see 5.2.4), not a shared domain value type. Circular wait becomes structurally impossible.
This applies identically to the in-JVM locks of 5.2.4 layer 1 and to the DB row locks — one ordering discipline, enforced in one place, covering both.
Secondary defence: a bounded retry on genuine deadlock/lock-timeout exceptions (3 attempts, jittered backoff), because ordering protects the reservation path but cannot guarantee no other statement in the system ever interleaves badly.
Tested by deadlockAvoidance (§15): two threads booking overlapping ranges from opposite ends; both must complete within timeout, neither sacrificed.
5.4 Starvation and liveness
Concern
Handling
Indefinite blocking
tryLock(timeout) — never lock(). Timeout → fail fast with INVENTORY_LOCK_TIMEOUT
Long critical sections
The DB row lock is held only for the statement plus the remainder of the transaction — no application logic inside it. The in-JVM lock wraps only the UPDATE call.
Lock held across I/O
Forbidden. The payment gateway call happens strictly outside any inventory lock. Holding a lock across a multi-second remote call is how throughput dies.
Lock map growth
Locks are per LockKey (5.2.4); entries reclaimed once uncontended (weak-valued map or explicit cleanup after release)
Fairness
Fair ReentrantLock on the reservation path so a thread cannot be starved indefinitely under sustained contention

5.5 Concurrency Control Chosen Per Path
Not one strategy applied uniformly — three, matched to contention profile.
Path
Contention
Mechanism
Reason
daily_inventory reserve / release
High — everyone wants the same popular room-night
Atomic conditional UPDATE
The invariant is a conditional increment. Expressing it in the statement removes the read-then-write window entirely. No version, no explicit lock.
Booking state transition
Low — one actor per booking, but sweeper and payment callback can race
Optimistic (@Version) + bounded retry (3×)
Conflict is rare; a version check is cheaper than a lock. The retry covers the sweeper/callback race of 4.4.
Payment state
Low, but webhook and status-poll genuinely race
Optimistic + bounded retry
Both paths can resolve the same payment concurrently; last-writer-wins would lose an outcome.
Property / RoomType / Guest
Low
Optimistic
Admin edits; conflict is an operator collision, surfaced not silently merged.
idempotency_record, webhook_event_log
Insert-only race
Unique constraint
The constraint is the serialisation point. Catch the violation, treat as replay.

Being able to say why each path differs is the point. "Optimistic everywhere" and "pessimistic everywhere" are both wrong answers, and pessimistic locking appears nowhere in this design — deliberately, because the one place it would have gone is better served by the conditional statement.
5.6 Virtual threads
Java 21 Executors.newVirtualThreadPerTaskExecutor() for blocking I/O:
Payment gateway calls
Outbound webhook delivery
Notification dispatch
Not used for the reservation transaction — virtual threads do not change lock or transaction semantics, and holding a DB transaction open across a virtual thread's blocking points is no better than on a platform thread.
Note for discussion: virtual threads largely remove thread exhaustion as a concern, which changes the justification for the bulkhead (Section 7).
5.7 On volatile and Atomic Variables — Deliberately Almost Absent
These are visible by their near-absence, so the reasoning is recorded rather than left to look like an oversight.
5.7.1 Why atomics are the wrong tool for inventory
The instinctive target is booked_units. An AtomicInteger there would be actively incorrect:
Problem
Detail
Wrong location for the state
The authoritative value is a database row, not a heap variable. An in-memory counter diverges on restart and is simply wrong across more than one instance.
Wrong operation
The invariant is booked_units <= total_units — a conditional increment. incrementAndGet() cannot express it; expressing it needs a CAS loop, which is a worse re-implementation of the row lock already in place.
Bypasses the real guarantee
The CHECK constraint (Section 5.2, layer 3) is what makes overbooking impossible. An in-memory counter routes around it.

Replacing a correct mechanism with a broken one is worse than adding nothing.
5.7.2 Where they would be legitimate — and why they are still absent
Construct
Plausible use
Why not used
volatile boolean
Stop flag for the reconciliation loop
Spring's lifecycle handles shutdown
AtomicReference
Hot-swappable retry-ladder config
Runtime config reload is not in scope
LongAdder
High-contention in-memory counters
Micrometer already does this correctly
AtomicInteger
Circuit-breaker attempt counters
Resilience4j already does this internally

Every legitimate use is already handled by a library in the stack. These are low-level primitives; in a Spring application whose state lives in a database behind locks, they are mostly the wrong layer.
5.7.3 The one place in-memory atomicity genuinely matters
lockRegistry.computeIfAbsent(key, k -> new ReentrantLock(true));

ConcurrentHashMap.computeIfAbsent is atomic. That atomicity is what prevents two threads from creating two different ReentrantLock instances for the same LockKey — which would silently defeat the entire locking scheme in Section 5.2 while appearing to work.
The critical concurrency primitive in this design is a method contract, not a keyword.
5.7.4 Position
volatile provides visibility, not atomicity. The consistency problem here is not visibility between threads sharing a heap variable — it is a compound check-then-act over state that lives in the database. That is solved with a row lock plus a check constraint. Sprinkling volatile or atomics around would signal reaching for remembered primitives rather than matching the tool to the actual problem.

6. Payment and the Provider SPI
6.1 Provider abstraction
public interface PaymentGatewayProvider {
    boolean supports(PaymentMethod method, BankCode bankCode);
    PaymentResult  initiate(PaymentRequest request);   // idempotent by reference id
    PaymentStatus  status(String providerReference);
    RefundResult   refund(RefundRequest request);
    ReversalResult reverse(ReversalRequest request);
    boolean verifyCallback(byte[] rawBody, String signature, String timestamp);
    String providerCode();
}

Implementations: MockCardProvider, MockUpiProvider, MockWalletProvider. Each signs outbound requests its own way and verifies callbacks its own way — which is the realistic case and the reason the interface includes both.
6.2 Discovery and routing
PaymentGatewayRouter receives List<PaymentGatewayProvider> by Spring injection and selects on supports(...).
Honest framing: this is a provider-registry / SPI-style plugin architecture, not classical Java SPI — there is no ServiceLoader and no META-INF/services. Spring's mechanism is chosen because it is idiomatic in a Boot application and gives lifecycle management for free. ServiceLoader would be the right choice if providers shipped as external JARs dropped on the classpath. This is stated in the README rather than left for the reviewer to ask about.
6.3 Adding a new provider
Implement PaymentGatewayProvider.
Annotate @Component.
No changes to router, service, controller or configuration. This is the extensibility claim the brief asks for, and it is verifiable by inspection.
6.4 Multi-property settlement — why routing exists
Property groups can be configured with different settlement providers (BankCode). This gives provider routing a genuine domain justification rather than being decoration: chain A settles through one gateway, chain B through another. Without this, "why does each booking need a different gateway?" has no good answer.

7. Resilience
7.1 Circuit breaker — one location only
Resilience4j, wrapping only the mock payment gateway call. It is the only unreliable remote dependency; a breaker anywhere else would be noise.
resilience4j.circuitbreaker.instances.paymentGateway:
  slidingWindowType: COUNT_BASED
  slidingWindowSize: 10
  failureRateThreshold: 50
  waitDurationInOpenState: 5s
  permittedNumberOfCallsInHalfOpenState: 3
  recordExceptions: [GatewayTimeoutException, GatewayUnavailableException]
  ignoreExceptions: [PaymentDeclinedException]

ignoreExceptions matters: a declined payment is a successful call with a negative business outcome. Counting declines as circuit failures would open the breaker during normal operation. This distinction is a frequent mistake.
7.2 Fallback must not guess
// Breaker open OR timeout → outcome is UNOBSERVED, not failed.
booking.transitionTo(PAYMENT_UNKNOWN);
return ApiResponse.pending(msgId, "PAYMENT_STATUS_UNKNOWN");

Never CONFIRMED (money may not have moved). Never FAILED (money may have moved). Resolution comes from the inbound webhook or the reconciliation job.
7.3 Timeout
Explicit timeout on every gateway call, set below the breaker's evaluation expectations.
Called out because it is the most commonly omitted resilience primitive: without a timeout, a hanging call never returns, the breaker never records a failure, and threads accumulate. The breaker is useless without it.
7.4 Retry — gated on idempotency
Operation
Retry
Condition
Gateway initiate
Yes
Only because the reference id is stable across retries. Without that, retry double-charges.
Gateway status
Yes
Read-only, naturally idempotent
Outbound webhook delivery
Yes
Exponential backoff + jitter
OptimisticLockException on booking state
Yes
Bounded, 3 attempts
Any non-idempotent DB write
No
Retrying produces duplicates

Backoff is exponential with jitter and capped. Fixed-interval retry across many threads produces a thundering herd on the recovering dependency.
Composition order: breaker outside retry. Retry attempts then count into the breaker's window. The reverse ordering means retries continue pointlessly against an open breaker.
7.5 Bulkhead — per provider, with an honest caveat
Semaphore bulkhead per PaymentGatewayProvider. A hanging UPI provider must not consume all capacity and starve card payments. Failure-domain isolation, which is what bulkhead is for.
Caveat stated openly: with virtual threads, thread exhaustion is largely not the concern it would be on a platform-thread pool. The bulkhead's remaining value here is backpressure and bounding concurrent load on a downstream dependency, not conserving threads. If that justification did not hold, the bulkhead would be removed rather than kept for appearance.
7.6 Stuck Transaction Resolution
A payment that neither succeeds nor fails is the normal case in real payment systems, not an edge case. PAYMENT_UNKNOWN (Section 3.3) is the state it lands in; this section is how it gets out. Without this, PAYMENT_UNKNOWN is a dead end and the circuit breaker fallback in 7.2 has nowhere to go.
7.6.1 The inventory decision — the real design problem
While a payment is unresolved, the room-nights are held. Two options, neither correct unconditionally:
Option
Consequence
Hold until resolved
A dead transaction blocks saleable inventory indefinitely. One stuck payment per room-night can starve a popular date.
Release immediately
If the payment later settles, money has been taken for a room since sold to someone else — a reversal and a failed booking.

Resolution: a bounded hold window, decoupled from the payment resolution window.
T+15m — inventory released. Room returns to sale. Booking remains PAYMENT_UNKNOWN; the customer still sees "pending".
Late settlement after release routes to LATE_SUCCESS_ON_EXPIRED_BOOKING (Section 9.2) → full reversal.
Rationale: inventory is perishable, money is recoverable. An unsold room-night is lost permanently; a wrongly-captured payment can be reversed. So the system optimises for keeping inventory liquid and accepts reversal cost as the price. This is a deliberate business decision and is stated as such in the README.
7.6.2 Status-check ladder
Bounded, escalating intervals — not a fixed-interval poll. Configuration, not hardcoded.
Attempt
Delay
Cumulative
Marker
1
+30s
30s
Gateway may simply be slow
2
+1m
1m30s


3
+2m
3m30s


4
+5m
8m30s


5
+7m
15m
Inventory hold expires
6
+15m
30m


7
+30m
1h


8
+1h
2h
Auto-reversal deadline
—
exhausted
—
→ MANUAL_REVIEW

payment.status-check:
  intervals: [30s, 1m, 2m, 5m, 7m, 15m, 30m, 1h]
  jitter-ratio: 0.2
  inventory-hold-window: 15m
  auto-reversal-deadline: 2h

Three independent decision points — conflating them is the modelling error to avoid:
T+15m — release inventory. Booking still pending.
T+2h — presume failure ("deemed failure"). Notify customer. Any later settlement reverses automatically.
Attempts exhausted — MANUAL_REVIEW.
Backoff carries jitter. Each status check passes through the same circuit breaker as the initiate call — if the gateway is down, status polling must not hammer it either.
7.6.3 New state: MANUAL_REVIEW
PAYMENT_UNKNOWN ──► CONFIRMED         settled, hold still valid
                ──► REVERSED          settled, hold already released
                ──► PAYMENT_FAILED    confirmed failed
                ──► MANUAL_REVIEW     attempts exhausted

MANUAL_REVIEW   ──► CONFIRMED | REVERSED | PAYMENT_FAILED     (admin-resolved)

MANUAL_REVIEW is deliberately not terminal. It is a parking state with a human in the loop. Making it terminal would leave the system permanently unable to reach the correct outcome. Resolved via POST /api/v1/admin/payments/{id}/resolve.
7.6.4 Resolution loop
for each payment in (PAYMENT_UNKNOWN) where nextAttemptAt <= now:

    if attemptNo > intervals.size():
        transition → MANUAL_REVIEW ; alert ; stop

    status = breaker.execute(() -> provider.status(paymentReference))

    SETTLED  → inventoryStillHeld(booking)
                 ? booking → CONFIRMED, ledger CHARGE
                 : booking → REVERSED,  reverse(LATE_SUCCESS_ON_EXPIRED_BOOKING)
    FAILED   → booking → PAYMENT_FAILED ; release inventory if still held
    PENDING  → if now > autoReversalDeadline:
                   presume failure ; release inventory ; arm late-success reversal
               else:
                   record attempt ; schedule nextAttemptAt
    ERROR    → record attempt ; schedule nextAttemptAt   (does not consume the budget)

ERROR (our call failed) is distinguished from PENDING (the gateway answered "still processing"). Only PENDING consumes the attempt budget — a network failure on our side is not evidence about the transaction.
7.6.5 Supporting record
Table: payment_status_check
  payment_id      BIGINT   NOT NULL
  attempt_no      INT      NOT NULL
  checked_at      TIMESTAMP
  next_attempt_at TIMESTAMP
  gateway_status  ENUM     SETTLED | FAILED | PENDING | ERROR
  response_body   TEXT     -- REDACTED before persist (see 12.6)
  correlation_id  VARCHAR
  INDEX idx_due (next_attempt_at, gateway_status)

Append-only, like the ledger. The full poll history for a disputed transaction is reconstructible — which is what an escalation to a payment partner actually requires.
7.6.6 Scope boundary
Built: the ladder as config, payment_status_check records, the 15m inventory-hold expiry (reusing the existing EXPIRED release path), MANUAL_REVIEW plus its admin resolution endpoint, and two tests — stuck → late settlement → reversal, and attempts exhausted → MANUAL_REVIEW.
Not built: workflow engine, SLA tracking, alerting integration, reconciliation-file ingest from the provider, customer-facing dispute flow. Real in production, scope creep here.
This section is what makes the circuit breaker, PAYMENT_UNKNOWN, reversals and the ledger a single coherent mechanism rather than four independent features.

8. Idempotency — Three Distinct Layers
Handling one layer and calling it done is the common failure. These are three different problems.
(a) Client → API
msgId in the request envelope is the idempotency key. There is deliberately no separate Idempotency-Key header — two identifiers for one concept is a design smell.
Table: idempotency_record
  msg_id        VARCHAR  UNIQUE NOT NULL
  request_hash  VARCHAR  NOT NULL
  status        ENUM     IN_PROGRESS | COMPLETED
  response_body TEXT
  created_at    TIMESTAMP

Situation
Behaviour
New msgId
Insert IN_PROGRESS, process, store response, mark COMPLETED
Replay, COMPLETED, same body hash
Return stored response. Do not reprocess.
Replay, IN_PROGRESS
409 CONFLICT — request in flight
Same msgId, different body hash
422 MSG_ID_PAYLOAD_MISMATCH. Surface the client bug; do not silently return the old response.

Retention: records are evicted after 24 hours (config). Unbounded growth on a dedupe table is a real production problem, and the eviction window is a stated assumption rather than an omission — it must exceed the longest plausible client retry window, which is why it is not minutes.
The UNIQUE index on msg_id is what serialises two genuinely concurrent requests carrying the same key. This is the same class of race as a concurrent insert on a unique index under retry — the constraint, not application logic, is what makes it safe.
(b) Service → gateway (outbound)
A paymentReference is generated once, persisted with the payment, and reused on every retry. Generating a fresh reference on retry is the standard double-charge bug.
Format follows a deterministic, collision-resistant scheme (bank code + timestamp + sequence), sized to the provider's field constraints.
(c) Gateway → webhook (inbound)
Gateways retry callbacks aggressively. The same PAYMENT_SUCCESS arriving twice must not double-confirm the booking or write two ledger entries.
Dedupe on UNIQUE (provider_code, event_id).
Handler is idempotent at the state machine level: CONFIRMED → CONFIRMED is a no-op.
Every callback is persisted to webhook_event_log before processing, with its signature-verification outcome, so a disputed transaction is reconstructible.

9. Refunds, Reversals and Ledger
9.1 Refund is not reversal


Refund
Reversal
Trigger
Customer cancels a confirmed booking
Transaction should not have stood, or outcome was ambiguous
Original transaction
Succeeded and settled
Succeeded, but the booking did not
Business meaning
"Money back, per policy"
"Undo — this was never valid"
Amount
Policy-determined; may be partial
Always full
Policy applied
Yes
No

Modelling these as one thing loses the distinction the business actually cares about.
9.2 Reversal scenarios handled
enum ReversalReason {
    RESERVATION_FAILED_AFTER_PAYMENT,   // paid, but reservation could not complete
    LATE_SUCCESS_ON_EXPIRED_BOOKING,    // breaker was open; hold lapsed; success arrived later
    DUPLICATE_CHARGE,                   // provider glitch or idempotency miss
    MANUAL_CORRECTION                   // admin-initiated
}

Scenario 2 is the one that gives the circuit breaker and PAYMENT_UNKNOWN a complete story: breaker open → unknown state → reconciliation → hold already gone → reverse.
9.3 Refund policy — Strategy
public interface RefundPolicy {
    BigDecimal calculate(Booking booking, Instant cancelledAt);   // in booking.getCurrency()
    String policyCode();
}

Implementations: FullRefundBefore48Hours, FiftyPercentBefore24Hours, NoRefundAfterCheckIn. Resolved per property group via RefundPolicyFactory, so different chains can carry different policies — directly satisfying the brief's "pluggable" requirement.
9.4 Ledger — simplified, append-only, immutable
@Entity
class LedgerEntry {                 // Long id + ledgerEntryUid (see 3.2); rows never updated
    Long          id;
    String        ledgerEntryUid;
    Long          bookingId;
    Long          paymentId;        // nullable for adjustments
    EntryType     type;             // CHARGE | REFUND | REVERSAL | ADJUSTMENT
    BigDecimal    amount;           // always positive
    String        currency;
    Direction     direction;        // CREDIT (in) | DEBIT (out)
    String        providerReference;
    Instant       occurredAt;
    String        correlationId;
}

Never UPDATE. Never DELETE. Corrections are new entries.
Why it earns a place despite not being requested:
Balance is derived, not stored. A mutable balance field loses history and cannot answer "why is this number what it is?"
Refunds and reversals get a coherent destination instead of merely flipping a status.
Over-refunding becomes structurally preventable. Invariant enforced before any refund or reversal entry is written: sum(REFUND) + sum(REVERSAL) <= sum(CHARGE) per booking.
Roughly four classes and one table.
Deliberately not double-entry. No chart of accounts, no counterparty accounts, no trial balance. There is no bank statement to reconcile against in this scope. This is stated explicitly in the README — otherwise a reviewer who knows accounting may assume the distinction was not understood rather than deliberately set aside.
9.5 Cancellation flow — ordering matters
cancel(bookingId, msgId)
  1. Idempotency check on msgId
  2. FSM validate: CONFIRMED → CANCELLED (reject if already terminal)
  3. RefundPolicy.calculate(booking, now)              [Strategy]
  4. Assert ledger invariant: refunded + this <= charged
  5. RELEASE INVENTORY — decrement booked_units per night, under lock
  6. Persist Refund(REQUESTED), transition booking → CANCELLED
  7. Gateway refund call — OUTSIDE any lock, idempotent reference
  8. Append LedgerEntry(REFUND, DEBIT)
  9. Publish BookingCancelledEvent                     [Observer]

Inventory is released before the gateway call, not after. The gateway is slow and unreliable; the room should become bookable immediately. If the refund subsequently fails, that is a money problem to reconcile — not a reason to keep saleable inventory blocked. This ordering is a deliberate business decision, stated in the README.
9.6 Audit records
Largely free, given the above:
Record
Content
ledger_entry
Immutable financial trail
booking_state_transition
(bookingId, fromState, toState, reason, actor, occurredAt) — append-only; makes the FSM auditable
webhook_event_log
Every inbound callback: redacted body (see 12.6), signature outcome, processing result
idempotency_record
Every state-changing request and its stored response

correlationId threads through all four.

10. Search / Discovery
10.1 Filter chain
public interface SearchFilter {
    boolean matches(Property property, SearchCriteria criteria);
    int order();                    // cheap filters first
}

Implementations: CityFilter, LocalityFilter, PriceRangeFilter, AmenityFilter, StarRatingFilter, GuestCapacityFilter, AvailabilityFilter.
SearchFilterChain receives List<SearchFilter> by injection, sorts by order(), and applies in sequence. Adding a filter is one new @Component and zero changes elsewhere — the brief's explicit requirement that new filters not require reworking search.
AvailabilityFilter runs last and is the only one touching inventory. It verifies booked_units + requestedUnits <= total_units for every night in the requested range — note + requestedUnits, not < total_units: a search for 2 rooms must exclude a property with only 1 free. A property with even one insufficient night in the range is excluded.
GuestCapacityFilter validates requestedUnits × roomType.maxGuests >= (adults + children).
PriceRangeFilter sums price_per_unit × units across the requested nights and compares the stay total, not a nightly rate — with per-night pricing (4.2.1), a nightly comparison would give inconsistent results across a weekend boundary.
10.2 Ordering rationale
Cheap in-memory predicates (city, star rating, capacity) run before the inventory query, so the expensive availability check operates on the smallest candidate set. Stated because filter ordering as a performance decision is a reasonable thing to be asked about.
10.3 Search-then-book is inherently racy — acknowledged, not hidden
Search reports availability at time T; the guest books at T+n. Inventory can be gone by then. This is not a bug — it is inherent to any system that does not hold inventory at search time, and every real booking platform has it.
Handled by being explicit rather than by pretending otherwise:
Booking re-validates availability atomically (5.2). Search results are advisory; the reservation statement is authoritative.
A failed booking returns INVENTORY_UNAVAILABLE naming the specific night that failed, so the client can re-search intelligently rather than retrying blindly.
README notes the alternatives considered: a short-lived soft hold at search time (adds a second hold lifecycle and lets a scraper starve inventory) versus optimistic display with clear failure (chosen).
Acknowledging this is worth more than silently having it.

11. API Layer
11.1 Request envelope
public record ApiRequest<T>(
    @NotBlank String  msgId,        // UUID — idempotency + correlation key
    @NotNull  Instant timestamp,    // client clock; replay window check
    @NotBlank String  channel,      // WEB | MOBILE | PARTNER
    @NotBlank String  version,      // payload contract version
              String  initiatorId,
    @NotNull @Valid T payload
) {}

11.2 Response envelope
public record ApiResponse<T>(
    String         msgId,           // echoed for client correlation
    String         correlationId,   // server-generated trace handle
    ResponseStatus status,          // SUCCESS | FAILURE | PENDING
    T              data,
    ApiError       error,
    Instant        respondedAt
) {}

public record ApiError(String code, String message, List<FieldError> fieldErrors) {}

Three deliberate choices:
PENDING is a first-class status, not an afterthought. It is what PAYMENT_UNKNOWN returns. Modelling only success/failure cannot express an unobserved outcome.
Error codes are a structured enum, not free text: INVENTORY_UNAVAILABLE, INVALID_STATE_TRANSITION, MSG_ID_PAYLOAD_MISMATCH, PAYMENT_TIMEOUT, INVENTORY_LOCK_TIMEOUT, REFUND_EXCEEDS_CHARGE. Stable for clients, greppable in logs.
correlationId is server-generated even though the client supplies msgId. Different purposes: msgId is the client's dedup handle; correlationId is the server's trace handle across ledger, webhook log and state transitions.
Envelope wrapping happens in ResponseEnvelopeAdvice, so controllers return plain DTOs and never hand-build wrappers. Hand-building in every method would be duplication, and Code Quality is HIGH-weight.
11.3 On apiType — correction from the initial idea
An apiType discriminator in the request body was considered and rejected as a client-supplied field.
In a REST API, POST /api/v1/user/bookings already identifies the operation unambiguously; asking the client to restate it duplicates information. The pattern is borrowed from NPCI/UPI, where it is genuinely necessary — those messages arrive over a single endpoint as XML and require an in-payload discriminator (ReqPay, ReqValAdd) to route. That constraint does not exist here.
Resolution: apiType is derived server-side in an interceptor from the matched route and stamped onto audit, ledger and idempotency records. The uniform operation discriminator for audit is retained; the redundant client obligation is not.
11.4 Three API categories
Not two — the third is easy to miss.
ADMIN   /api/v1/admin/**       property owners and operators
USER    /api/v1/user/**        guests
SYSTEM  /api/v1/webhooks/**    machine-to-machine



ADMIN
USER
SYSTEM (webhook)
Caller
Operator
Guest
Payment provider
Auth model
Role-based (stubbed)
Session (stubbed)
HMAC signature
Error semantics
Normal REST
Normal REST
Return 2xx fast, process async — non-2xx triggers aggressive provider retries
Rate expectations
Low
Moderate
Bursty, retry-heavy

Endpoints:
ADMIN
  POST   /api/v1/admin/properties                onboard property
  PATCH  /api/v1/admin/properties/{id}           update
  POST   /api/v1/admin/inventory/extend          roll horizon forward
  POST   /api/v1/admin/inventory/reprice         re-run PricingStrategy over a date range
  PATCH  /api/v1/admin/inventory/{roomTypeId}    override one night's rate or unit count
  POST   /api/v1/admin/sweeper/run               trigger hold-expiry / completion sweep
  POST   /api/v1/admin/bookings/{id}/reverse     manual reversal
  POST   /api/v1/admin/reconciliation/run        resolve PAYMENT_UNKNOWN
  POST   /api/v1/admin/payments/{id}/resolve     resolve MANUAL_REVIEW
  POST   /api/v1/admin/guests/{id}/redact        erasure (12.6.3)
  GET    /api/v1/admin/payments/stuck            list PAYMENT_UNKNOWN + MANUAL_REVIEW
  GET    /api/v1/admin/ledger?bookingId=         ledger view

USER
  POST   /api/v1/user/properties/search          discovery
  POST   /api/v1/user/bookings                   create (holds inventory)
  POST   /api/v1/user/bookings/{id}/pay          pay
  POST   /api/v1/user/bookings/{id}/cancel       cancel + refund
  GET    /api/v1/user/bookings/{id}              read

SYSTEM
  POST   /api/v1/webhooks/payment/{providerCode} inbound callback

Mirrored in packages (controller.admin, controller.user, controller.webhook — see 2.3) so the separation is structural, not merely a URL convention.
Authorisation is out of scope per the brief. A @RequireRole(ADMIN) annotation with a trivially-stubbed interceptor is included, plus one README line: role separation is structural; enforcement stubbed since authz is out of scope. The thinking is demonstrated without spending hours on Spring Security.

12. Webhooks and Cryptography
12.1 Webhook envelope — symmetric
public record WebhookEnvelope<T>(
    String  eventId,        // provider's id — the dedup key
    String  eventType,      // PAYMENT_SUCCESS | PAYMENT_FAILED | REFUND_COMPLETED
    String  providerCode,
    Instant eventTime,
    String  version,
    T       payload
) {}

Same type used inbound and outbound. One envelope, one signer, both directions.
12.2 Signature travels in headers, not the body
X-Signature:  sha256=<hmac-hex>
X-Timestamp:  <epoch-millis>
X-Provider:   MOCK_CARD

The HMAC is computed over the raw request body bytes, so the signature cannot live inside the payload being signed. This matches standard practice and requires reading the body as raw bytes before deserialisation (ContentCachingRequestWrapper).
12.3 Verification sequence
1. Timestamp within ±5 minutes                  → else REPLAY_WINDOW_EXCEEDED
2. eventId not already seen (nonce/dedupe)      → else return 200, no-op
3. HMAC-SHA256 over RAW body bytes with provider secret
4. MessageDigest.isEqual(expected, received)    ← constant-time comparison
5. REDACT the payload                           ← must happen AFTER step 4
6. Persist redacted body + outcome to webhook_event_log
7. Process idempotently through the FSM
8. Return 200 regardless of business outcome

Two details worth stating:
Constant-time comparison (MessageDigest.isEqual, not String.equals) — a short-circuiting comparison leaks signature bytes through timing.
Persist before process — a callback that crashes processing must still be reconstructible.
Redact after verify, never before. The HMAC is computed over the original bytes; redacting first would break the signature check. Order is: read raw → verify → redact → persist. Reversing steps 4 and 5 is a real trap.
12.4 Outbound signing
Notifications emitted to merchants are signed with the same mechanism, delivered with exponential-backoff-plus-jitter retry on non-2xx, and logged.
12.5 Field encryption — optional
AES-GCM on sensitive payment metadata at rest, key from configuration. Included only if time remains after tests. A key-management story beyond "externalised config" would not be defensible at this scope and will not be invented.
12.6 Personal Data and Sensitive Payment Data
This system holds more personal data than a bare payment flow does, and one requirement here contradicts the immutable ledger of Section 9.4. That contradiction is resolved structurally in 12.6.3 rather than left as a trade-off.
12.6.1 What personal data exists, and why some of it is worse than it looks
Location
Data
Guest
name, email, phone, address, optionally date of birth
Booking
guest reference, stay dates, property
Payment
VPA, masked instrument, billing name
Audit tables
whatever the provider payload carried
Logs
whatever was interpolated into a log statement

Two items deserve more weight than they usually get:
Stay dates + property is a location history — who was where, on which nights. Arguably the most sensitive derived data in the system, and it is a by-product of the core domain rather than a field anyone chose to collect.
Email and phone are stable cross-system identifiers. Far more linkable than a masked card number, and they cannot be tokenised away because flows depend on them.
12.6.2 Sensitive payment data — the precise rule
Two obligations, frequently conflated:
Data
Rule
Sensitive authentication data — CVV / CVV2 / CVC, full track data, PIN or PIN block
Never retained after authorisation. Not in logs, not in the DB, not encrypted. No compliant way to store it post-auth.
PAN (card number)
May be stored, but must be unreadable — masked, truncated, tokenised or strongly encrypted. This design uses first-six/last-four masking.

411111XXXXXX1111 in a log is acceptable; a full PAN is not; a CVV is not storable at all. "Nothing sensitive is logged" is too blunt — the distinction between never-storable and must-be-unreadable is the one that matters.
12.6.3 Erasure vs immutability — the architectural consequence
Section 9.4 states the ledger is append-only: never UPDATE, never DELETE. Data-protection principles require honouring an erasure request. Both cannot hold if personal data sits inline in immutable records.
Resolution: no append-only table contains personal data. Only opaque identifiers cross that boundary.
guest                        MUTABLE, redactable in place
  id, name, email, phone, address, redacted_at

booking                      references guest_id — no inline PII
ledger_entry                 references booking_id — no inline PII
booking_state_transition     ids, states, reasons only
payment_status_check         redacted payload only
webhook_event_log            redacted payload only

Erasure becomes: overwrite the guest row's identifying fields with tombstones, retain the id, stamp redacted_at. The financial and audit trail remains intact and immutable while the person becomes unidentifiable. Both obligations are satisfied rather than traded off.
Alternative considered — crypto-shredding: encrypt each subject's personal data under a per-subject key; erasure deletes the key. More flexible, and the correct answer where personal data genuinely must live inside immutable records. Not used here: it requires a key store with its own lifecycle, and the reference-only separation above achieves the same outcome with far less machinery.
The resulting design rule — one line, real architectural teeth:
Personal data enters an append-only table only as an opaque identifier.
Cheap to hold now; expensive to retrofit once audit history exists.
12.6.4 Implementation
@Retention(RUNTIME) @Target({FIELD, RECORD_COMPONENT})
public @interface Sensitive {
    Masking value() default Masking.FULL;   // FULL | PAN | LAST4 | EMAIL | PHONE | NAME
}

Mechanism
Purpose
@Sensitive + custom Jackson serializer
Masking is the default wherever a field is serialised — logs, audit records, API responses
Explicit toString() on every DTO carrying personal or payment data
Never Lombok @ToString on these types. An accidental log.info("{}", request) is the most common leak path there is.
LogRedactionConverter (Logback)
Regex backstop over rendered log lines — catches PAN-, email-, phone- and VPA-shaped strings that a hand-written log statement slipped through
PayloadRedactor
Applied to provider payloads before they reach webhook_event_log or payment_status_check
GuestRedactionService
Tombstones identifying fields; asserts referential integrity and ledger balance survive
Domain exceptions carry codes and ids, never payload objects
Stack traces are a silent leak channel

12.6.5 Two layers, in defence order
Structural — sensitive fields are typed and annotated, so masking is the default and exposure requires an explicit act.
Backstop — a log-appender filter, because layer 1 depends on developer discipline and a single hand-written log.debug bypasses it.
Layer 2 does not replace layer 1. It exists because layer 1 will occasionally be forgotten.
12.6.6 Demonstrated on synthetic data
The gateway is mocked, so no real card or customer data exists. The mocks nevertheless emit payloads containing PAN-shaped, CVV-shaped and contact-shaped fields, so the redaction path executes rather than merely being described — with a test asserting none of it reaches captured log output or a persisted audit row.
A design that stays clean only because there happens to be no real data demonstrates nothing.
12.6.7 Scope boundary
Built: the reference-only separation of 12.6.3 · @Sensitive annotation and serializer · DTO toString() discipline · log-appender backstop · payload redaction before audit persist · GuestRedactionService with a ledger-integrity test · the leak test.
Not built: consent management, data-subject-request workflow, retention scheduler, crypto-shredding and its key store, tokenisation vault, HSM/KMS integration, key rotation, PCI-DSS scoping or network segmentation. Real production obligations; scope creep here.
On legal framing: India's DPDP Act 2023 is the relevant statute and its principles — purpose limitation, data minimisation, erasure — are what drive the design decisions above. The README discusses design consequences rather than asserting compliance status, since implementation rules and enforcement timelines have been evolving. Not legal advice; to be verified independently.

13. Persistence
13.1 Store choice: H2 in MySQL compatibility mode
Rationale, in order of weight:
The brief says so explicitly: in-memory persistence (collections or H2) is fine — do not spend time on a production database. Ignoring an explicit instruction is a judgment cost, and judgment is being assessed.
Reviewer friction is the real risk. ./gradlew bootRun must work first time. Requiring a running MySQL, a schema, credentials and a working Compose file puts Correctness (HIGH-weight) at risk for reasons unrelated to the code.
Nothing is lost. H2 supports @Version optimistic locking, row-level write locks, conditional UPDATE with a rowsAffected result, composite and unique indexes, and CHECK constraints. Every concurrency claim in this document is demonstrable on H2 — including the atomic conditional reservation of 5.2.1 and the DB-level deadlock behaviour of 5.3 (surfaced as a lock timeout rather than MySQL's error 1213, which the test accounts for).
A application-mysql.yml profile and a schema-mysql.sql reference file are committed — including partitioning DDL — but not active by default. The MySQL knowledge is visible in the repo without imposing setup cost.
13.2 Schema — inventory, booking and line items
CREATE TABLE daily_inventory (
  id              BIGINT  PRIMARY KEY AUTO_INCREMENT,
  room_type_id    BIGINT  NOT NULL,
  stay_date       DATE    NOT NULL,          -- property-local calendar date (4.5)
  total_units     INT     NOT NULL,
  booked_units    INT     NOT NULL DEFAULT 0,
  price_per_unit  DECIMAL(12,2) NOT NULL,    -- written by PricingStrategy (4.2.1)
  currency        CHAR(3) NOT NULL DEFAULT 'INR',
  CONSTRAINT uq_inventory_slot  UNIQUE (room_type_id, stay_date),
  CONSTRAINT ck_not_overbooked  CHECK  (booked_units <= total_units),
  CONSTRAINT ck_non_negative    CHECK  (booked_units >= 0),
  CONSTRAINT ck_price_positive  CHECK  (price_per_unit > 0)
);

CREATE TABLE booking (
  id              BIGINT  PRIMARY KEY AUTO_INCREMENT,
  booking_uid     CHAR(36) NOT NULL,         -- what every API/lookup actually uses (3.2)
  guest_id        BIGINT  NOT NULL,          -- reference only; no PII inline (12.6.3)
  property_id     BIGINT  NOT NULL,
  room_type_id    BIGINT  NOT NULL,
  check_in        DATE    NOT NULL,
  check_out       DATE    NOT NULL,
  units           INT     NOT NULL,
  adults          INT     NOT NULL,
  children        INT     NOT NULL DEFAULT 0,
  total_amount    DECIMAL(12,2) NOT NULL,
  state           VARCHAR(32)   NOT NULL,
  hold_expires_at TIMESTAMP     NULL,        -- 4.4
  version         BIGINT  NOT NULL DEFAULT 0,
  created_at      TIMESTAMP     NOT NULL,
  CONSTRAINT ck_dates      CHECK (check_out > check_in),
  CONSTRAINT ck_units      CHECK (units >= 1),
  CONSTRAINT ck_adults     CHECK (adults >= 1),
  CONSTRAINT uq_booking_uid UNIQUE (booking_uid)
);

CREATE TABLE booking_line_item (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_id     BIGINT NOT NULL,
  stay_date      DATE   NOT NULL,
  units          INT    NOT NULL,
  price_per_unit DECIMAL(12,2) NOT NULL,     -- SNAPSHOT at booking time (3.4.1)
  line_total     DECIMAL(12,2) NOT NULL,
  CONSTRAINT uq_line_night UNIQUE (booking_id, stay_date)
);

Notes on specific choices:
booking_uid (and the equivalent *_uid column on every other top-level table — property, room_type, guest, payment, ledger_entry, none shown above since their schemas are built in later phases) is the two-id convention of 3.2: id is the storage-internal surrogate key, booking_uid is what every API and lookup actually uses. daily_inventory is the deliberate exception — its natural key is (room_type_id, stay_date), already enforced below by uq_inventory_slot, so it gets no separate uid column.
ck_not_overbooked is the correctness backstop of 5.2.4 layer 3 — the database refuses to overbook regardless of application logic, including from a hand-written query.
No version on daily_inventory. The atomic conditional UPDATE (5.2.1) needs none, and an unread column invites a question with no good answer.
uq_line_night prevents two line items for the same night on one booking — a duplicate would silently double the total.
price_per_unit duplicated onto the line item is deliberate denormalisation: the inventory row's price can change later; the line item must not.
13.3 Indexes, with stated purpose
Index
Purpose
uq_inventory_slot (room_type_id, stay_date)
Guarantees one row per room-night; the counter cannot be silently duplicated
idx_inventory_lookup (room_type_id, stay_date)
Availability hot path (served by the unique index)
idx_property_city_rating (city, star_rating)
Search path; leading column matches the mandatory filter
uq_idempotency_msg_id (msg_id)
Idempotency lookup and the serialisation point for concurrent same-key requests
uq_webhook_event (provider_code, event_id)
Inbound callback dedupe
idx_ledger_booking (booking_id, occurred_at)
Ledger reads and the refund-invariant sum
idx_booking_state (state, created_at)
Reconciliation scan for stale PAYMENT_UNKNOWN
idx_status_check_due (next_attempt_at, gateway_status)
Due-poll scan for the status-check ladder
idx_booking_hold_expiry (state, hold_expires_at)
Sweeper scan for abandoned holds (4.4)
idx_booking_checkout (state, check_out)
Sweeper scan for CONFIRMED → COMPLETED
idx_line_item_booking (booking_id)
Line-item fetch and total reconstruction
idx_booking_guest (guest_id)
Erasure impact lookup (12.6.3)

Each index exists for a named query. None are speculative.
On idx_inventory_lookup: it duplicates uq_inventory_slot and would be dropped in production — the unique index already serves the lookup. It is listed separately here only to make the query-to-index mapping explicit, and the README says so rather than leaving a redundant index unexplained.
13.4 Seed data
30–50 properties across 5–6 Indian cities, varied amenities, star ratings, room types and price bands. Committed as a static fixture loaded under @Profile("demo").
Deterministic, committed seed data — not a live API call. Reasons: no genuinely free no-key live hotel API exists (every real-time option caps volume, gates access behind an application, or is a frozen dataset — typical free tiers are ~50 requests/day); a live dependency breaks reviewer reproducibility; the brief explicitly places real third-party integration out of scope; and this system owns its inventory, so sourcing inventory from an aggregator is domain-incoherent.
If real place names are wanted, they are pulled once, offline from OpenStreetMap / Overpass (no key required) and the output committed. A free API used as a build-time data source, never as a runtime dependency.

14. Design Pattern Map
Pattern
Location
Justification
Strategy
RefundPolicy, PricingStrategy (writes price_per_unit per night, 4.2.1), payment method handling
Brief demands "pluggable" refund policy, a bonus pricing strategy, and payment methods "behind a common abstraction"
Chain of Responsibility
SearchFilterChain
Brief demands new filters without reworking search
Factory
RefundPolicyFactory, PaymentGatewayRouter
Resolve implementation by runtime discriminator
Finite State Machine
BookingStateMachine + transition table
Brief demands "well-defined state transitions"
Repository
Spring Data JpaRepository<Entity, Long> per aggregate (repository.*)
Brief mandates it explicitly; auto-implemented rather than hand-wired through a port and adapter (see 0.1)
Builder
Property, Booking, SearchCriteria (Lombok @Builder)
Many optional fields; keeps constructors sane
SPI / provider registry
PaymentGatewayProvider + router
Multi-bank integration; new provider = one class
Observer
Spring ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)
Decouples notification and audit from booking flow; AFTER_COMMIT prevents notifying on a rolled-back booking
Template Method
Shared booking pipeline skeleton
Only if it emerges naturally. Not forced.
Uniform hierarchy
Owner → PropertyGroup → Property
Single property as group-of-one; no branching

14.1 Patterns deliberately excluded
Pattern
Why not
Value Object
Used for DateRange, Money, UnitCount, GuestCount, Location and typed ids in an earlier revision (see 0.1), then deliberately removed: their validation moved onto the entity directly or became a Bean Validation annotation, in exchange for a simpler, more conventional entity layer at the cost of Money's compile-time currency-mismatch guarantee (3.2). Listed here rather than silently dropped from the pattern table, because a reviewer who compares this document against an earlier version should find the change explained, not just absent.
Abstract Factory
Solves "create matched sets of related objects across product families." There is one family here, not families of families. Using it would be unjustifiable over-engineering, and the justification would not survive a follow-up question. Plain Factory is correct.
Hand-rolled Singleton
Spring beans are already singleton-scoped. Writing double-checked locking in a Spring application signals unfamiliarity with the container.
Full Composite
Requires arbitrary node/leaf nesting. A hotel does not contain hotels.
Decorator / Visitor / Mediator
No natural seam. Not going looking for one.

Stating exclusions with reasons is deliberate: it demonstrates pattern judgment rather than pattern recall.

15. Testing Strategy
15.1 Highest-value tests
Test
What it proves
Concurrent single-unit race — 20 threads race the last available unit on one room-night; assert exactly 1 success, 19 INVENTORY_UNAVAILABLE, booked_units == total_units
The atomic conditional UPDATE holds. The single most valuable test in the project.
Concurrent multi-unit race — 3 units free, 4 threads each requesting 2 units; assert exactly 1 succeeds, booked_units == 2, and no partial allocation
Multi-unit correctness. A single-unit test cannot detect partial allocation; this is the test that justifies modelling units at all.
Multi-night atomicity — 3-night booking where night 2 is full; assert rowsAffected = 0 on night 2, transaction rolls back, and nights 1 and 3 are unchanged
All-or-nothing (5.2.3); no compensating-decrement bug
Deadlock avoidance — two threads booking overlapping ranges from opposite ends, repeated under load; both complete within timeout, neither sacrificed to a DB deadlock
Total lock ordering works at the DB row-lock level (5.3), where the risk actually lives
Multi-night partial availability — one night full in the middle of the range; whole booking rejected, no partial reservation
Range semantics correct
FSM illegal transitions — every disallowed pair throws
State machine is total, not permissive
Idempotent payment — same msgId twice → one charge, one ledger entry, same response
Idempotency layer (a) works
Webhook replay — same eventId twice → one confirmation, one ledger entry
Idempotency layer (c) works
Tampered signature rejected / stale timestamp rejected
HMAC verification correct
No sensitive data leak — drive a full booking and payment with PAN-, CVV- and contact-shaped fields; assert none appears in captured log output, webhook_event_log or payment_status_check
Redaction is enforced, not aspirational
Guest erasure preserves the ledger — redact a guest with a settled booking and a refund; assert identifying fields are tombstoned, the booking and ledger rows survive, and sum(CHARGE) - sum(REFUND) is unchanged
The separation in 12.6.3 actually works
No PII in append-only tables — reflective/structural assertion that ledger_entry, booking_state_transition and payment_status_check expose no @Sensitive field
The design rule is enforced, not just documented
Redact-after-verify ordering — a payload that would fail HMAC if redacted first still verifies
The sequence in 12.3 is implemented correctly
One lock per inventory key — concurrent computeIfAbsent on the same key yields the identical lock instance
The atomicity the locking scheme depends on (5.7.3) actually holds
Breaker open → PAYMENT_UNKNOWN — never CONFIRMED, never FAILED
Fallback does not guess
Stuck txn → late settlement → reversal — payment unresolved past the hold window, inventory released, settlement arrives late; assert booking REVERSED, full reversal, ledger balanced
The stuck-transaction path resolves correctly
Status checks exhausted → MANUAL_REVIEW — gateway returns PENDING for every attempt; assert MANUAL_REVIEW, not a silent dead end, and admin resolution works
Bounded resolution with a human exit
Inventory released at hold expiry — room becomes bookable by another guest while the first booking is still PAYMENT_UNKNOWN
The perishable-inventory trade-off is real, not documented-only
ERROR does not consume attempt budget — our call fails vs gateway says PENDING; only the latter increments attemptNo
The distinction is implemented, not just described
Hold expiry releases inventory — unpaid booking past TTL; sweeper expires it and the room becomes bookable again
4.4 works; inventory does not leak on abandonment
Sweeper vs in-flight payment — payment settles concurrently with the expiry sweep; assert exactly one outcome, no double-release, no confirmed-but-released booking
The optimistic-lock race of 4.4 is handled
CONFIRMED → COMPLETED reachable — booking past checkout is completed by the sweeper
The state is not dead code
Price snapshot immutability — book, then reprice the inventory night; assert the booking's total and line items are unchanged
3.4.1 holds
Per-night pricing — weekend-surge strategy over a Fri–Sun stay; assert line items differ per night and the total matches their sum
PricingStrategy genuinely attaches
Property-local dates — server clock at UTC near midnight; assert today_local for an IST property is the correct calendar day
4.5 works; the classic UTC/IST trap
Guest capacity — 5 guests, 2 rooms at maxGuests 2; rejected
Capacity validated against units
Refund invariant — refund exceeding remaining charge rejected
Ledger invariant enforced
Refund policy substitution — same cancellation, different policy, different amount
Strategy is genuinely pluggable
Filter chain composition — adding a filter changes results without touching search
Extensibility claim verifiable

15.2 Approach
Domain and application logic: plain JUnit 5, no Spring context. Fast.
Fixed Clock injected everywhere — no Instant.now() in domain code, so time-dependent policy tests are deterministic.
Concurrency tests: CountDownLatch to release all threads simultaneously, ExecutorService with a fixed pool, assertions on aggregate outcome.
Repository tests against H2.
A small number of @SpringBootTest slices for envelope wrapping and webhook verification.

16. Out of Scope — With Production Evolution Notes
Each of these is genuinely known and deliberately not built. The README carries this section verbatim, because explaining precisely where each would go is a stronger signal at this level than building any of them prematurely.
Not built
Where it would go in production
Microservices
Split along property-catalog / inventory / booking / payment. Booking↔inventory↔payment currently share a transaction boundary; splitting them requires a saga with compensating actions — the reversal machinery in Section 9 is exactly that compensation, already modelled.
gRPC
Inter-service calls once split. There is no pre-existing port interface to promote (0.1 dropped that layer) — extracting a service means defining a gRPC contract from the relevant service-layer method signatures directly and wrapping the existing service class as its implementation, which is mechanical but is genuinely a new step rather than a reuse of something already in place.
Kafka + Avro + DLQ
The domain events already published in-process (BookingConfirmedEvent, PaymentSettledEvent) become topic messages. Avro schemas with a registry for contract evolution; DLQ for poison messages after bounded retry. Notification and reporting become consumers.
Redis
Read-through cache for property catalog and hot availability. Distributed locking deliberately not proposed as a primary mechanism — Redlock's correctness under partition and clock skew is contested; the DB constraint remains the guarantee.
Reporting store + partitioning
Booking history is append-heavy and queried by date range. CDC (Debezium → Kafka) into a reporting store; bookings PARTITION BY RANGE (YEAR(check_in)) in MySQL for partition pruning on date-bounded reports and cheap old-partition drops for retention. Not built here: H2 has no partitioning support, and at seed-data volume partition pruning would demonstrate nothing measurable.
Distributed tracing
Correlation IDs are already threaded through logs, ledger and webhook records — the propagation contract exists. OpenTelemetry spans would attach to it once there are process boundaries to cross. Nothing distributed to trace today.
mTLS / transport encryption
Between services once split. No inter-service hop exists; payload-level HMAC signing covers the one real trust boundary (the webhook).
Live third-party hotel data
See 13.4. Build-time data sourcing only.
Load testing / high-throughput tuning
The single-statement compare-and-set of 5.2.1 is already the primary mitigation and is implemented. Expected remaining bottleneck is row-lock contention on a single popular room-night. Further options, none implemented: shard the counter into K sub-rows per room-night and pick one at random (trades exact-availability reads for write throughput), queue reservation requests per inventory key, or cache availability with a short TTL and accept stale search results. Not measurable at this scope; unmeasured tuning would be theatre.
Auth / authz
Out of scope per brief. Role separation is structural (Section 11.4); enforcement stubbed.
Docker
Not required by the brief. Added last, only if tests and README are complete.


16.1 Where the build has actually reached
Every planned phase is built: 0 through 9, plus the optional Phase 10 extras. Nothing in the
implementation plan of 17 remains outstanding. What exists today: the scaffold and entity core (0/1, as revised in 0.1); onboarding, inventory
materialisation and pricing (2) — the ownership hierarchy including the group-of-one rule,
per-night DailyInventory rows with the check constraints of 5.2.4 layer 3 enforced by the
database, three pricing strategies behind a registry, the eager bounded-horizon materialiser,
admin endpoints for onboarding/extend/reprice/rate-override, and a demo seed profile; booking,
reservation and locking (3) — the atomic conditional UPDATE of 5.2.1, total night ordering per
5.3, all-or-nothing multi-night reservation, multi-unit reservation, per-night price snapshots
on line items, the optional in-JVM lock of 5.2.4 layer 1, and the BookingSweeper of 4.4 for
hold expiry and CONFIRMED → COMPLETED; payment, SPI and resilience (4) — the
PaymentGatewayProvider SPI with three mock providers and a router (6), a hand-written circuit
breaker standing in for Resilience4j (7.1, see 16.2), timeout and idempotency-gated retry via
Spring's native @Retryable (7.3/7.4), per-provider @ConcurrencyLimit as the bulkhead (7.5), the
full stuck-transaction resolution of 7.6 — the status-check ladder, payment_status_check
records, the inventory-hold-window release on its own clock, the auto-reversal deadline, and
MANUAL_REVIEW with its admin resolution endpoint — and idempotency layer (a), client → API,
via msgId and an idempotency_records table (8a); and cancellation, refund, reversal and the
ledger (5) — three RefundPolicy strategies behind RefundPolicyFactory resolved per property
group (9.3), the append-only LedgerEntry with the sum(REFUND)+sum(REVERSAL)<=sum(CHARGE)
invariant checked before any debit is written (9.4), the cancellation flow of 9.5 transcribed
step-for-step, and ReversalService as the single place every scenario in 9.2 goes through —
late settlement after an expired hold, and admin-initiated MANUAL_CORRECTION for a duplicate
charge on an already-settled booking (which required a small, explicitly-documented addition
to the Phase 1 BookingState transition table — see 16.4); and the API layer (6) — the request
envelope (ApiRequest<T>) and response envelope (ApiResponse<T>) with ResponseEnvelopeAdvice
wrapping every controller return value, a structured ErrorCode enum driving both
GlobalExceptionHandler's status mapping and every DomainException subclass, a server-generated
correlationId threaded through logs (MDC), IdempotencyRecord and LedgerEntry, apiType derived
per-route by an interceptor reading an @Api annotation rather than a client-supplied field, the
msgId migration that made booking creation idempotent for the first time (see 16.5), and the
trivially-stubbed @RequireRole/RoleInterceptor role separation of 11.4; and, as of Phase 7,
webhooks and the data protection layer (7) in full — the reference-only PII separation of
12.6.3 (Guest gained real profile fields, redactable in place), @Sensitive and its Jackson
serializer, the PayloadRedactor/LogRedactionConverter two-layer defence of 12.6.5,
GuestRedactionService with the ledger-integrity test 12.6.3 calls for, HMAC signing and
verification with constant-time comparison, webhook_event_log with the same persist-before-
process discipline the idempotency table established, idempotency layer (c) on
(provider_code, event_id) plus the FSM-level no-op guard a unique constraint alone cannot
provide, and disabled-by-default signed outbound delivery (see 16.6); and, as of Phase 8,
search and discovery (8) in full — the SearchCandidate/RoomTypeCandidate mutable-candidate
architecture resolving design doc 10.1's unimplementable literal filter signature, the seven
SearchFilter/BatchSearchFilter beans run in order() sequence by SearchFilterChain, the
two-query-per-collection fix for PropertyRepository's fetch-join duplication bug (see 16.7),
and the advisory, non-locking, cap-not-paginate search endpoint at
POST /api/v1/user/properties/search; and, as of Phase 9, the hardening pass in full - the nine
previously-missing highest-value tests from 15.1 (breaker-open fallback, the full
stuck-transaction ladder including the MANUAL_REVIEW-reachability bug that phase found and
fixed, the sweeper-versus-payment optimistic-lock race, payment idempotency, the refund
invariant as a real running total, and refund-policy substitution), the full README this
document's own 18 specifies, a committed and verified MySQL reference profile
(application-mysql.yml, schema-mysql.sql), IdempotencyRecordSweeper closing the
payment.idempotency.retention gap, and the final sweep (gradlew's executable bit, the
package-info gaps in controller.admin/advice/user, and a stale Payment.createdAt/updatedAt gap
in Instant.now() usage - see 16.8 for the full account of what that phase's own tests found);
and, as of Phase 10, the three optional extras in 17's own order - OpenAPI/Swagger UI via
springdoc (with OpenApiConfig correcting a generated document that was wrong about both the
response envelope and two 201s, see 16.9), AES-GCM field encryption at rest on every Guest PII
column (with the column widening and the DATE-to-VARCHAR change that forced), and a verified
multi-stage Dockerfile running as a non-root user on the same in-memory H2 the default profile
uses. What is not built: nothing that was planned. The remaining gaps are the ones 16's own
table names as deliberate exclusions, plus key rotation for the encryption added above.
Two structural notes on what phase 2 added beyond the tree in 2.3: controllers are grouped
by caller (controller.admin, controller.user, controller.webhook, controller.advice) as
11.4 requires, and a minimal GlobalExceptionHandler mapping error codes to HTTP status was
pulled forward from phase 6 — without it every domain exception surfaces as a 500, which
would make the phase-2 endpoints look broken rather than scoped. The envelope itself was still
phase 6's job, and adopting it did not change any controller's method signature or return
type (11.2) — only what each one is annotated with and, for state-changing endpoints, what its
parameter type wraps.

16.2 Phase 3 findings that change later phases
Spring Framework 7 ships its own resilience annotations —
org.springframework.resilience.annotation.Retryable, @ConcurrencyLimit and
@EnableResilientMethods — in spring-context itself. @Retryable already provides bounded
retries with exponential backoff, jitter, a maximum delay and per-exception include/exclude
filtering, which is the whole of what 7.4 asks for, and it is what the reservation path uses
today for lock-timeout retry. Taken with the Phase 0 finding that resilience4j-spring-boot4
does not exist on Maven Central, Phase 4 should plan on the framework's own annotations plus
a hand-written circuit breaker rather than assuming Resilience4j is available: @Retryable
covers 7.4, @ConcurrencyLimit covers the bulkhead of 7.5, and the breaker of 7.1 is the one
piece with no framework equivalent.
A structural consequence worth recording: retry must wrap the transaction, not sit inside
it, or every attempt after the first runs in a transaction that is already marked
rollback-only. That is why booking creation is split across two beans — BookingService
carries @Retryable and BookingCreator carries @Transactional. Annotating one method with
both would leave the nesting to advisor ordering.

16.3 Phase 4 findings
The @Retryable / @ConcurrencyLimit plan from 16.2 held up: both are used as anticipated
(PaymentGatewayClient for gateway initiate/status, each mock provider's initiate/status for
per-provider bulkhead) with no surprises. The hand-written PaymentCircuitBreaker mirrors
Resilience4j's own COUNT_BASED/failure-rate/HALF_OPEN model deliberately, so swapping in the
real library later — if resilience4j-spring-boot4 ever ships — is a drop-in change against
the same CircuitBreakerProperties. Verified by hand against a running instance: the breaker
opens after crossing its failure-rate threshold, fast-fails (sub-40ms) while OPEN, and closes
again after a successful HALF_OPEN trial.
Two more platform-version findings, in the same spirit as 16.2:
Spring Boot 4.1 ships Jackson 3, which relocated every package from com.fasterxml.jackson.*
to tools.jackson.* — ObjectMapper is now tools.jackson.databind.ObjectMapper — and made
JacksonException (the new common base, replacing JsonProcessingException) an unchecked
RuntimeException. Code written against Jackson 2 idioms (importing com.fasterxml.jackson.
databind.ObjectMapper, catching JsonProcessingException) fails to compile, not merely to
resolve a dependency; every online example still assumes Jackson 2.
Spring Framework 7 renamed HttpStatus.UNPROCESSABLE_ENTITY to UNPROCESSABLE_CONTENT (the
RFC 9110 wording for 422), keeping the old constant only as deprecated. -Werror catches this
immediately; a codebase without it would carry a silent deprecation warning.
The stuck-transaction resolution of 7.6.6 is built exactly to its stated scope: the ladder as
config, payment_status_check records, the inventory-hold-window release on its own clock
(decoupled from both the ladder and the auto-reversal deadline, per 7.6.2), MANUAL_REVIEW
with its admin resolution endpoint, and the SETTLED/FAILED/PENDING/ERROR branches of the
resolution loop exactly as pseudocoded in 7.6.4 — including ERROR not consuming the ladder's
attempt budget. One scope line honestly drawn: 7.6.4's "any later settlement reverses
automatically" after the auto-reversal deadline is not built as a standing arm-then-reverse
mechanism — a payment that hits the deadline resolves straight to PAYMENT_FAILED (with
inventory released if not already), which is the deadline's terminal answer, not a promise to
keep watching. Building the watch-and-reverse-later machinery on top would be exactly the kind
of workflow-engine scope creep 7.6.6 already rules out.
What ships as a real capability gap, not a corner cut: the reconciliation loop's SETTLED
branch correctly decides CONFIRMED vs REVERSED (by checking whether this payment's inventory
was already released), and the admin MANUAL_REVIEW resolution endpoint makes the same
decision — both verified against a running instance, including the late-settlement-after-
release case landing on REVERSED. But neither writes a LedgerEntry, and PaymentGatewayProvider
.refund/.reverse still throw UnsupportedOperationException. The booking-level story is
complete; the money-movement bookkeeping is Phase 5's job, not simulated here.

16.4 Phase 5 findings
Phase 4's gap is now closed: ReversalService.reverse and CancellationService both write the
LedgerEntry the previous phase deferred, and the mock providers' refund/reverse now return a
real result instead of throwing. RefundPolicy's signature was extended from the phase-4-era
(Booking, Instant) to (Booking, ZoneId, Instant): computing "hours until check-in" needs the
check-in date resolved to an instant in the property's own zone, not the server's, for the
same reason design doc 4.5 cares about property-local dates everywhere else — this was a
signature this document had itself locked in an earlier revision, refined now that the
policies were actually being written rather than left unbuilt.
A structural gap, found only by running the late-settlement scenario end to end rather than
by reading the code: the reconciliation loop's SETTLED branch called ReversalService.reverse
without ever having recorded the CHARGE for the payment that had just settled. Since
LedgerService.assertWithinInvariant computes remaining balance as sum(CHARGE) minus what is
already refunded/reversed, a booking with zero charges on its ledger has zero remaining
balance — so the very first reversal attempt against a legitimately-just-settled payment was
rejected with REFUND_EXCEEDS_CHARGE, on a payment that had, in fact, just settled. Fixed by
recording the charge before deciding what happens to the booking, in both the automated
ladder and the admin MANUAL_REVIEW resolution path: money moved in either case, and the
charge is real regardless of what happens to the booking next.
A second gap, this one structural rather than a bug: design doc 9.2 lists DUPLICATE_CHARGE
and MANUAL_CORRECTION as reversal reasons, both of which can apply to a booking that already
settled normally — but the Phase 1 BookingState transition table only wired REVERSED from
PAYMENT_UNKNOWN and MANUAL_REVIEW, with no path from CONFIRMED. Building the admin manual-
reversal endpoint the design document calls for meant adding CONFIRMED -> REVERSED to that
table. This is a deliberate, documented change to a state machine that shipped in Phase 1
with full cartesian-product test coverage — the existing test's mirrored table was updated to
match rather than left to fail, since a broken existing test is worse than a corrected one.
Verified live end to end: full refund with ample notice, a zero-refund same-day cancellation
skipping the gateway and the ledger entirely (nothing moved, nothing to record), the 50%
policy at exactly the 24-hour boundary, admin manual reversal moving a CONFIRMED booking to
REVERSED with a balanced ledger, and — after the fix above — a genuinely late settlement
producing both a CHARGE and a REVERSAL entry that net to zero.

16.5 Phase 6 findings
The headline finding is not subtle: booking creation was not idempotent before this phase.
CreateBookingRequest carried no key at all — msgId lived only on CancelBookingRequest and
InitiatePaymentRequest, each with a hand-rolled "if present, dedupe" branch — so a retried
create genuinely held a second set of room-nights. Moving msgId out to ApiRequest<T> and
making it @NotBlank closed this for free: every state-changing endpoint gets the idempotency
key by construction, the two hasMsgId conditionals in PaymentService and CancellationService
disappeared, and BookingCreator.create gained the same begin/complete wrapper the other two
already had. BookingIdempotencyApiTest exercises exactly this end to end and would have failed
against the pre-Phase-6 code.
One placement decision the migration forced: idempotency now lives in BookingCreator, not
BookingService, even though BookingService is the class a caller actually calls. BookingCreator
is the @Transactional method Phase 3 split out specifically so @Retryable (on BookingService)
never wraps an already-rolled-back transaction (16.2). IdempotencyService.begin runs
REQUIRES_NEW and therefore commits independently of whatever transaction called it; if the
begin/complete pair sat in BookingService instead, a lock-timeout retry would call begin again
against an msgId whose IN_PROGRESS row already committed on the failed first attempt, and the
retry would see IdempotencyConflictException instead of retrying cleanly. Putting the wrapper
on the same bean as the transaction it guards — mirroring PaymentService.pay and
CancellationService.cancel exactly — sidesteps this rather than papering over it.
The PENDING mechanism (design doc 11.2, task spec §5.1) is a marker interface
(PendingAware.pending()) on the response DTO, not a reflective check or an import of
PaymentState into the advice. Two reasons, not one: first, ResponseEnvelopeAdvice would
otherwise need to import PaymentState (or worse, switch on a class name) and grow a new branch
every time a later phase adds another kind of unresolved outcome — a refund stuck mid-flight,
say. Second, and more important, deciding "is this pending" is a business judgement about what
a given state means, and that judgement belongs with the DTO that owns the state, not with a
web-layer adapter reasoning about a domain enum it should not need to know exists.
apiType required one addition beyond design doc 11.3's own list: GET_PAYMENT. The design
document's ApiType enum (task spec §4.4) lists PAY_BOOKING for the pay endpoint but nothing for
PaymentController.get(paymentUid) — an oversight in the source list, not a deliberate omission,
since every other GET already has its own constant (GET_PROPERTY, VIEW_INVENTORY, GET_BOOKING).
Added it rather than leaving one handler stamped UNKNOWN for no reason.
Two places where the letter of the task spec could not be followed and a substitute was
chosen instead, both flagged as deviations rather than silently done:
  - ApiRequest<Void> for the two no-body admin triggers (sweeper run, reconciliation run) is
    unsatisfiable: ApiRequest.payload() is @NotNull, and java.lang.Void has no non-null
    instance a client could ever send — every such request would fail validation before
    reaching the handler, which is the opposite of what "give them an envelope" was for. Used
    dto/EmptyPayload (a zero-field record) instead, so a client sends "payload": {}. One extra
    file beyond the phase's own 18-file list, justified by the contradiction it resolves.
  - The task spec's §3.1 locked service signatures at (String msgId, T request), but its own
    §4.5 requires apiType and correlationId to reach IdempotencyRecord too, and recommends
    bundling them into a small RequestMeta record "built once in the controller" rather than
    threading three parallel parameters through three services. Resolved in favour of §4.5:
    BookingService.create, PaymentService.pay and CancellationService.cancel all take a
    RequestMeta (msgId, apiType, correlationId) in the msgId parameter's position, not a bare
    String. It is still a plain, ApiContext-free record a unit test constructs with a literal
    value, so §3.1's actual concern — no web-scope coupling into service, no broken testability
    — holds regardless of which of the two shapes carries it. RequestMeta and its sibling web.Api
    annotation are, like EmptyPayload, extra files beyond the enumerated 18.
Deliberately not wired: BookingSweeper.sweep() and PaymentReconciliationService.run(), despite
task spec §9.1 saying the two no-body admin triggers "belong under idempotency ... like the
rest." They get the envelope (msgId recorded, correlationId and apiType stamped in scope) but
not an IdempotencyService.begin/complete wrapper. Reason: idempotency layer (a)'s replay
contract is "same msgId returns the identical stored response, does not reprocess" — correct
for create-or-charge-once operations, actively wrong for a scan-and-process sweep, where a
second call with the same msgId should run a fresh pass over whatever is due now, not replay a
stale response from an earlier pass that may have processed a completely different set of
payments. Treating "belongs under idempotency" as "gets the envelope, not necessarily the
dedupe layer" is the reading applied here; flagged in case a later reviewer expected otherwise.
LedgerEntry.correlationId now genuinely correlates instead of being three different business
uids wearing a "correlationId" column name. Before this phase, LedgerService.append's last
parameter was payment.getPaymentUid() / refund.getRefundUid() / reversal.getReversalUid()
depending on entry type — a stable identifier, but not the request's trace handle, and not
comparable across entry types for the same operation. recordCharge/recordRefund/recordReversal
now take an explicit correlationId: the originating request's for PaymentService and
CancellationService (via RequestMeta), and — since PaymentReconciliationService's scheduled
pass has no request to inherit one from — a UUID minted once per run() invocation and reused
across every LedgerEntry and PaymentStatusCheck that pass writes, so one sweep is one trace
handle rather than N unrelated ones.
No pre-existing test's assertions needed to change in substance, only call sites — worth
recording because it is not the outcome the task spec anticipated ("regression duty: run the
full suite... fix them; do not weaken assertions"). The reason turned out to be structural: all
17 pre-Phase-6 test classes exercise services and repositories directly, never through MockMvc
or the HTTP layer, so ResponseEnvelopeAdvice, GlobalExceptionHandler's new envelope shape and
RequestEnvelopeAdvice never touch any of them — there was no assertion about response shape to
strengthen. The only mechanical change was threading a fresh RequestMeta (a random msgId per
call, since @NotBlank is now enforced) through every bookingService.create(...) call site across
BookingServiceTest, BookingSweeperTest, InventoryReservationConcurrencyTest and
ReservationWithoutInJvmLockTest — 31 call sites, all via one substitution. The one place this
mattered for correctness rather than just compiling: InventoryReservationConcurrencyTest and
ReservationWithoutInJvmLockTest race many threads against literally the same CreateBookingRequest
payload. Giving every thread the same msgId would have collapsed the race into "one request, N
replays" — the second thread through would see IdempotencyConflictException, not
InventoryUnavailableException — and silently invalidated the single most valuable test in the
project (15.1) without any test failure to notice it. Each thread gets its own generated msgId
for exactly this reason.
A genuine bug this phase's own manual smoke test caught that no automated test did: registering
CorrelationIdFilter at @Order(Ordered.HIGHEST_PRECEDENCE), as design doc 11 and an early draft
of this code both did, throws ScopeNotActiveException the moment it touches the @RequestScope
ApiContext bean — but only outside of a MockMvc-driven test. A plain servlet Filter runs before
DispatcherServlet ever gets to bind RequestContextHolder for the current thread, so a
request-scoped bean has nothing to resolve against yet; MockMvc's own ServletTestExecutionListener
pre-binds RequestContextHolder for the whole test method regardless of filter order, which is
exactly why all six new web-layer tests passed against the broken ordering and only a real
./gradlew bootRun + curl smoke test surfaced it. Fixed by registering Spring's own
RequestContextFilter in WebConfig at the true HIGHEST_PRECEDENCE and moving CorrelationIdFilter
to HIGHEST_PRECEDENCE + 10. Recorded here because it is exactly the kind of gap "the tests pass"
can hide, and the fix (a filter registered ahead of another filter, both ahead of the servlet)
has nothing to do with Spring MVC and would not have been caught by any @WebMvcTest no matter
how many were added — only a real container catches a real container's request-binding order.
Two more Jackson 3 / Spring Framework 7 platform findings, in the spirit of 16.2/16.3's:
  - jackson-annotations is not part of the tools.jackson.* relocation. Jackson 3 moved
    jackson-core and jackson-databind to the tools.jackson.core Maven group and the
    tools.jackson.* package root (16.3 already found this for ObjectMapper), but
    jackson-annotations deliberately stayed on the Jackson 2.x groupId and package
    (com.fasterxml.jackson.annotation) — the jackson-databind 3.x POM says so explicitly
    ("Annotations remain at Jackson 2.x group id"). @JsonInclude on ApiResponse therefore
    imports from com.fasterxml.jackson.annotation, not tools.jackson.databind.annotation as a
    mechanical find-and-replace of every other Jackson import in this codebase would suggest.
  - Spring Boot 4's test-slice annotations moved packages by feature module. WebMvcTest and
    AutoConfigureMockMvc are no longer under org.springframework.boot.test.autoconfigure.web.servlet
    (where every Boot-3-era example still has them); they now ship in the spring-boot-webmvc-test
    module under org.springframework.boot.webmvc.test.autoconfigure. Several other MVC exception
    types used by the new GlobalExceptionHandler handlers moved with Spring Framework 7's own
    reorganisation: HttpRequestMethodNotSupportedException is org.springframework.web (no longer
    .bind), HandlerMethodValidationException and MethodArgumentTypeMismatchException are
    org.springframework.web.method.annotation, and NoResourceFoundException is
    org.springframework.web.servlet.resource. None of this resolves to a compile error pointing
    at the actual problem — it resolves to "package does not exist" against the old import, which
    reads like a missing dependency rather than a moved class unless you already know to look.
Nothing found in this phase looks wrong for a later one. The one open question worth flagging
for Phase 7: PaymentWebhookController will need its own apiType constant (PAYMENT_WEBHOOK
already exists in the enum, unused until then) and will sit outside @RequireRole entirely,
authenticated by HMAC instead of the X-Role header — RoleInterceptor's "no header, let it
through" default means it would technically not block an unauthenticated webhook call today if
someone pointed a browser at it directly, which is fine only because the controller does not
exist yet. Phase 7 should not rely on RoleInterceptor for anything on that path.

16.6 Phase 7 findings
Guest was a two-field placeholder — id and guestUid, its own Javadoc saying profile data was
"built in a later phase" — and 12.6's entire redaction story was undemonstrable against it.
A redaction test against an entity with nothing to redact proves nothing; the PII fields
(fullName, email, phone, address, dateOfBirth, redactedAt) had to be built before they could
be protected, so this phase built the data before building the guard around it, in the order
the task spec's own §0.1 called out as the trap to avoid (build webhooks first, retrofit
redaction onto an audit table already storing raw payloads). Booking creation now optionally
carries GuestDetails, resolveGuest populates a fresh guest row from it, and — the one rule
worth restating because it is easy to get backwards — supplying both an existing guestUid and
GuestDetails on the same request silently ignores the details rather than updating the
existing person's record. A booking request is not a profile-update endpoint.
The mock providers carried no PAN-, CVV- or contact-shaped data at all before this phase —
PaymentRequest was (providerReference, method, bankCode, amount, currency, simulate) and
PaymentResult was (outcome, providerReference, message), neither with anywhere sensitive data
could even live. Added: optional instrument fields on PaymentRequest (cardNumber, cvv,
billingName, vpa, walletId, contactPhone — all null on every real call site, since nothing in
this codebase collects real instrument data from a guest), and a providerPayload field on
PaymentResult carrying a synthetic, obviously-fake JSON blob (4111 1111 1111 1111,
asha@example.com) that AbstractMockProvider builds itself when the request's own fields are
absent. This is what makes 12.6.6's "the redaction path executes rather than merely being
described" literally true: NoPiiInAuditOrLogsTest drives a real flow through it and the test
would fail — verified by hand — if PayloadRedactor were stubbed to identity.
That last change forced a signature change beyond the task spec's own file list:
PaymentGatewayProvider.status(String) returned a bare GatewayOutcome, with no way to carry a
payload at all. Changed it to return PaymentResult (the same type initiate() already
returned), rippling into PaymentGatewayClient.callStatus and PaymentReconciliationService's
polling loop. The alternative — leaving status() unable to carry a payload — would have made
it structurally impossible for sensitive-shaped data to ever reach
PaymentStatusCheck.responseSummary, which is the one pre-existing table this phase was
explicitly told to wire redaction into. Noting this because it is a real deviation from "only
touch the files listed," made because the alternative made a stated requirement unbuildable.
A related loose end, not a bug: PaymentGatewayProvider.verifyCallback(byte[], String, String)
— declared back in Phase 4, thrown as UnsupportedOperationException with a comment that it
would be "exercised starting in the phase that... receives webhooks" — is still unused. This
phase's actual signature-verification design is centralised (crypto.SignatureVerifier reading
per-provider secrets from WebhookProperties), not routed through the per-provider gateway
interface Phase 4 anticipated. The two designs both work; they simply never met. Left
verifyCallback exactly as it was rather than wiring it to something it was never called by, so
a later phase (or reviewer) does not mistake it for load-bearing. It is a reasonable removal
candidate whenever Phase 9 hardening touches the gateway package.
A genuine bug, not merely a design tension, surfaced by actually running the webhook flow
rather than by reasoning about it: PaymentSettlementService.settle/fail, extracted verbatim
from the status-check ladder and resolveManualReview (§8.4, see below), silently did nothing
when called from InboundWebhookService. The ladder and resolveManualReview both load Payment
and Booking inside a transaction they already hold open (TransactionTemplate.execute), so
those entities stay attached for the whole call chain and Hibernate's dirty-checking flushes
the mutations at commit. InboundWebhookService has no such ambient transaction — it calls
PaymentRepository.findByProviderReference as a bare repository call, which opens and commits
its own mini-transaction and hands back an already-detached entity. Passing that detached
entity into PaymentSettlementService.settle, itself a separate @Transactional method opening
a brand-new transaction, mutates an object with no persistence context to be dirty-checked
against — the state transitions and the ledger entry looked correct in memory and were never
persisted. WebhookIdempotencyTest caught this immediately (booking stuck at PAYMENT_UNKNOWN
instead of reaching CONFIRMED) the first time it was run against real Spring transaction
semantics rather than assumed by inspection. Fixed by adding settleByProviderReference and
failByProviderReference to PaymentSettlementService: they do the Payment/Booking lookup
*inside* their own transactional boundary, so the entities are attached for the whole method,
mutations included. The ladder and resolveManualReview keep calling the original
settle(Payment, Booking, String)/fail(Payment, Booking) overloads unchanged, since they
already hold their own transaction open before the entities are ever loaded.
The @RequestBody byte[] choice over ContentCachingRequestWrapper (design doc 12.2's literal
suggestion): a deliberate simplification, not an oversight. byte[] gives the exact bytes that
were signed with no filter, no wrapper, and no risk of the cache being consumed (by a logging
filter, say) before the signature check reads it. ContentCachingRequestWrapper solves a
different problem — re-reading a stream-based body multiple times — that does not exist here,
since deserialisation happens once, from the byte[], after verification. Confirmed rather than
assumed: RequestEnvelopeAdvice.supports() only returns true for ApiRequest-typed bodies, so a
byte[] parameter is untouched by it regardless.
The three Phase 6 exemptions (task spec §11), all real decisions:
  - ResponseEnvelopeAdvice's basePackages narrowed from the bare controller package to {
    controller.admin, controller.user } explicitly, excluding controller.webhook by omission
    rather than by a negative check — a future controller package added under controller.*
    is not silently swept in. This forced ProbeController, the Phase 6 test fixture living
    directly under controller, to move to controller.user (five test files' imports updated
    accordingly) — a controller outside the two covered packages would no longer prove
    anything about enveloping either way.
  - WebConfig excludes /api/v1/webhooks/** from RoleInterceptor explicitly, rather than
    relying on the interceptor's existing "missing header passes" stub default. The behaviour
    is identical either way today; the explicit exclusion is what stops a future tightening
    of that stub (adding real enforcement) from silently starting to block webhooks, which
    authenticate by HMAC and were never meant to carry a role header at all.
  - WebhookExceptionHandler is the one place a 2xx-everywhere API deliberately breaks its own
    rule: SignatureVerificationException (bad signature, unknown provider, stale timestamp)
    returns 401, the single non-2xx response this codebase's SYSTEM category ever sends,
    because a request that will never be accepted must not be retried, and acking one 200
    would tell a forger its forgery worked. Everything else — including a genuine bug in our
    own processing — acks 200, because a provider's retry policy amplifying our own failure
    into a storm is a worse outcome than fixing it from the event log at our own pace.
PaymentSettlementService (§8.4): extracted settle/fail — transition the payment, transition
the booking, release inventory if held, write the ledger charge, reverse via ReversalService
when the hold already lapsed — from logic that used to live separately inside
PaymentReconciliationService's pollGateway SETTLED branch and inside resolveManualReview's
SETTLED branch. No behavioural change for either of those two callers: same transitions, same
ledger call, same reversal condition, in the same order. What is new is the FSM-level
idempotency guard at the top of both methods (return immediately if already SETTLED/FAILED) —
harmless for the ladder and admin paths, which already gate on specific pre-states before
calling in, but load-bearing for the webhook path, where a provider can legitimately deliver
two different eventIds for the same settlement and the (provider_code, event_id) unique
constraint has no way to catch that, since it is a different key both times.
REFUND_COMPLETED is logged and acknowledged but never processed — a deliberate scope
boundary, not a gap. Refunds already have a synchronous owner: CancellationService writes the
REFUND ledger entry itself as part of the cancellation flow (design doc 9.5, Phase 5). Wiring
REFUND_COMPLETED to write a second entry from the async webhook path would open a second write
path into the same ledger for the same logical event, with no clear rule for which one wins
or how to detect the other already ran. The webhook_event_log row still records that the
callback arrived, which is what 9.6's audit trail is for; it simply does not drive state.
Two Jackson 3 / Logback platform findings, in the spirit of 16.2/16.3's own:
  - JsonNode.asText() is deprecated in Jackson 3 in favour of asString() (and asString(String)
    for the default-value overload). Compiles and runs under Jackson 2 idioms, then fails the
    build under -Werror the moment anything calls the deprecated method — a second, distinct
    trap from the tools.jackson.* package relocation 16.3 already found, in the same class.
  - PatternLayoutBase.getDefaultConverterMap() (the natural way to register a custom Logback
    conversion word programmatically, e.g. in a test) is deprecated in the Logback version
    this Spring Boot 4.1.1 pulls in, in favour of getInstanceConverterMap(), which is keyed by
    Supplier<DynamicConverter> rather than a class-name String. LogRedactionConverterTest uses
    the new form; logback-spring.xml's own <conversionRule converterClass="..."/> is
    unaffected, since that XML attribute is a different, still-current registration path.
Everything else found was a judgement call rather than a defect, recorded here so a reviewer
does not read it as an oversight: HmacSigner.verify decodes hex before comparing specifically
so a same-value signature in a different hex case still verifies (design doc's own "handles
case differences... for free") — this is not the same claim as "a wrong-case signature is
still accepted despite being wrong," and HmacSignerTest is careful to vary only the hex
digits, never the sha256= prefix, to keep the two claims from collapsing into each other in
the test itself. WebhookProperties.outbound.secret and .outbound.retry are not part of the
YAML shape the task spec's own §7.2 gives verbatim; both were added because HmacSigner.sign
needs some secret to sign outbound notifications with, and @Retryable's numeric attributes
must be compile-time constants (the same constraint PaymentGatewayClient's own Javadoc already
states), so the retry literal on WebhookHttpSender mirrors config rather than reading it.
WebhookHttpSender itself is a small addition beyond the task spec's own file list, for the
same reason PaymentGatewayClient is a separate bean from its callers: @Retryable on a method
that WebhookDispatcher called on itself would be silently skipped by Spring's self-invocation
limitation, exactly as it would have been for PaymentSettlementService's transaction boundary
above — the same class of AOP-proxy trap catching two different pieces of this phase.
Nothing found here looks wrong for Phase 8 or 9. The one thing worth flagging forward: Phase 8
(search) will want AvailabilityFilter to read DailyInventory the same read-only way admin
inventory views already do; nothing in this phase touches that path, so there is no known
interaction, but it is the next place a similarly quiet transaction-boundary assumption could
hide.

16.7 Phase 8 findings
The headline finding is that design doc 10.1's own SearchFilter signature — matches(Property,
SearchCriteria) — is not implementable as written, and this was known going in rather than
discovered mid-phase: a boolean per (Property, SearchCriteria) pair cannot express three of the
seven required filters. GuestCapacityFilter must drop individual room types, not the whole
property, when a room type is too small but a sibling room type on the same property is not.
AvailabilityFilter and PriceRangeFilter both need per-room-type, per-stay-total state that
outlives the filter that computed it and must be visible to a later filter in the chain — a
pure predicate has nowhere to put that. The resolution is the mutable SearchCandidate /
RoomTypeCandidate pair this phase built: a candidate that owns a prunable list of surviving
room types, plus two fields (stayTotal, availableUnits) that AvailabilityFilter populates and
PriceRangeFilter later reads. SearchFilterChain still runs a plain List<SearchFilter> in order
and still adds a new filter with zero changes elsewhere (design doc 10.1's actual goal) — only
the shape of what a filter receives and may mutate changed, not the chain's own logic.
BatchSearchFilter exists for exactly one reason: AvailabilityFilter is the one filter that must
hit the database, and doing that one candidate at a time is the textbook N+1 this phase was
told explicitly not to reintroduce (design doc 10.2). Extending SearchFilter with an
applyBatch(List<SearchCandidate>, SearchCriteria) method lets that filter see every surviving
candidate at once — one findByRoomTypeIdInAndStayDateBetween call across all of them — while
every other filter stays a plain per-candidate predicate and never needs to know batching
exists. SearchFilterChain treats the two uniformly (instanceof BatchSearchFilter, same
empty-after-every-filter pruning either way), so the escape hatch cost the chain nothing.
PriceRangeFilter reading AvailabilityFilter's output (candidate's stayTotal, set by whichever
filter ran immediately before it) is a real, load-bearing ordering dependency, not an
implementation detail — moving PriceRangeFilter earlier than AvailabilityFilter silently
breaks it (every stayTotal reads null, so PriceRangeFilter's null-is-non-matching rule would
empty every candidate). This is exactly why order() is 80 for PriceRangeFilter and 70 for
AvailabilityFilter rather than the 60 an ungrounded reading of the filter table might suggest,
and it is called out in both filters' own Javadoc, not left for a future reader to infer from
the numbers alone.
SearchQueryCountTest measures three prepared statements per search, not the two ("one property
fetch plus one inventory fetch") this phase's own task spec assumed going in. The third query
exists because of a genuine bug this phase found and fixed in PropertyRepository: fetch-joining
two collections (roomTypes, amenities) on one root in a single query is a SQL cartesian
product, and in this Hibernate version select distinct on that query did not collapse the
duplication back out — a property with 2 room types and 2 amenities came back with each room
type listed twice, silently, no exception thrown. That was caught by
PropertySearchServiceTest asserting on the actual room-type list content, not by anything
counting queries, which is exactly why an assertion on data shape and not just query success is
what caught it. The task spec's own warning here (two List-typed collections throw
MultipleBagFetchException) is real but incomplete: amenities is a Set, not a List, so no
exception was ever thrown, and the corruption would have shipped silently without that specific
assertion. The fix — one fetch-join query per collection, both against the same persistence
context, so the second query's results merge onto the already-managed entities from the first —
is the standard answer to "eagerly load two to-many collections", and it costs one extra query
per search, constant regardless of candidate count. SearchQueryCountTest was written and its
threshold chosen against this corrected, three-query reality rather than forcing the original
two-query assumption; it asserts "well under one query per candidate" (fewer than 6, for a
6-candidate fixture) rather than pinning the literal 3, so a future incidental query that is
still O(1) does not make the test flaky over nothing.
Section 5.4's property-local-date handling (LocalDate.now(clock.withZone(property.zone())),
never the server's zone) turned up nothing the booking path's equivalent handling had not
already established — SearchPropertyLocalDateTest is close to a restatement of the booking
path's own zone test, built the same way (MutableClock, two properties in different IANA
zones, one instant that is different calendar days in each). The one difference worth noting:
AvailabilityFilter checks a property-local "does this stay start before today" rule per
candidate inside a batch operation, rather than at the single-property granularity the booking
path checks it at, so the same correctness rule had to be re-proven at a different call shape,
not assumed to transfer automatically from a passing booking-path test.
One acceptance-criterion grep is worth a note rather than a silent pass: grep -rn
"AvailabilityFilter\|PriceRangeFilter\|CityFilter" SearchFilterChain.java returns a hit, but
it is a Javadoc line ({@code CityFilter}, {@code StarRatingFilter}, ...) explaining the
ordering rationale in prose, not an import, field, or any other real reference — the class has
zero compiled dependency on any concrete filter, working only through List<SearchFilter> and
instanceof BatchSearchFilter. The criterion's actual intent (the chain must not hardcode
knowledge of specific filters) holds; a literal grep is just not precise enough to distinguish
prose from code here, and this note exists so that fact is not merely inferred by whoever reads
that grep result next.
Nothing found in this phase is believed wrong for Phase 9. The one thing worth flagging
forward, mirroring 16.6's own forward note: Phase 9 is documentation and polish, not new
persistence code, but if any later phase does add a second multi-collection eager fetch
anywhere in the codebase, PropertyRepository's Javadoc is the place that already explains why
the obvious one-query version silently corrupts data in this Hibernate version, and is worth
reading before repeating the mistake rather than re-discovering it the same way.

16.8 Phase 9 findings
The most important thing in this report, per this phase's own instruction: the nine new tests
found a real bug in the stuck-transaction path, not a cosmetic one. PaymentReconciliationService
.reconcileOne checks the auto-reversal deadline before it ever polls the gateway or checks
whether the status-check ladder is exhausted ("presumed failure takes precedence over the
ladder... regardless of how many attempts remain"). With the shipped default configuration -
eight intervals summing to roughly 2h00m30s of ladder time, and a 2-hour auto-reversal-deadline
- that ordering meant the deadline always won by about thirty seconds: MANUAL_REVIEW via ladder
exhaustion was unreachable in practice, even though AbstractMockProvider's own Javadoc
explicitly documents STUCK_FOREVER as driving a payment "to exhaustion and MANUAL_REVIEW". Every
stuck payment would instead resolve to a presumed-FAILED state roughly thirty seconds before the
final rung could ever fire. StuckTransactionResolutionTest's ladder-exhaustion scenario failed
against the original 2h value, which is what surfaced this; fixed by widening
payment.status-check.auto-reversal-deadline to 210m (comfortably past the ~3h a full nine-check
cycle actually takes to flag MANUAL_REVIEW), with the reasoning recorded directly on the
property in application.yml so a future config change does not silently reintroduce the same
near-miss. This is a config-value fix, not a logic change - the precedence order itself
(deadline before ladder) is left exactly as designed, since 7.6.2's own reasoning for it still
holds; only the two numbers' relationship to each other was wrong.
A second, smaller bug the same tests turned up: PaymentService.newPaymentAttempt never set
createdAt/updatedAt on a new Payment, so both fell through to that entity's own @PrePersist
Instant.now() fallback - real wall-clock time, never the injected Clock, on every single payment
attempt ever created, including under every MutableClock-driven test in this suite. Contrast
BookingCreator, which explicitly sets Booking.createdAt from Instant.now(clock); Payment simply
never got the equivalent line. Fixed by setting both fields explicitly in newPaymentAttempt.
Not fixed, and flagged rather than silently left: Payment's @PreUpdate still stamps updatedAt
with a bare Instant.now() on every subsequent touch (every state transition), which cannot be
made clock-deterministic without either injecting a clock into a non-Spring-managed JPA
lifecycle callback (not possible in the ordinary sense) or moving to a clock-aware auditing
listener (a real architectural change, not a one-line fix, and out of this hardening phase's own
"no new production capability" boundary). Booking has no equivalent @PreUpdate stamping at all,
so it does not share this gap - Payment is the only entity with it, and it is now the only
place in the codebase where the Phase 0 "Clock is injected everywhere" rule is knowingly not
fully honoured, for a reason recorded here rather than left for a future reader to rediscover by
grepping.
Section 3.4's ordering assertion held exactly as claimed: CancellationOrderingTest's InOrder
verification confirms InventoryReservationService.release is called before the gateway's
refund() call, and a second test confirms a refund the gateway rejects (GatewayOutcome.FAILED)
still leaves that release standing and the booking CANCELLED - the booking's fate and the
room's availability are decided independently of whether money has actually moved back yet,
exactly as CancellationService's own Javadoc claims.
[Corrected in 16.9: this claim was true of the runs it describes but incomplete - the test was
later found to allow only one of the two legitimate ways the payment can lose the race, and
failed on a heavily loaded machine during Phase 10. The product behaviour it describes below
was and is correct; the test was not.]
SweeperPaymentRaceTest was not flaky: fifteen iterations (the task spec's own 10-20 guidance),
each a fresh property/booking pair raced with a CountDownLatch exactly as
InventoryReservationConcurrencyTest already does, passed cleanly on the first run with no
retries needed. The optimistic-lock behaviour under real contention was exactly what Booking's
@Version column is supposed to produce: whichever transaction's UPDATE commits first wins
outright, and the loser's entire transaction rolls back rather than partially applying - when
the sweeper lost, PaymentService.pay's uncaught ObjectOptimisticLockingFailureException rolled
back the whole payment attempt (no Payment row, no ledger entry, nothing partially written);
when the payment won, BookingSweeper's own catch(OptimisticLockingFailureException) inside
expireOne counted it as skipped and moved on, exactly as BookingSweeperTest's single-threaded
"already confirmed" scenario already implied but never proved concurrently.
Design doc 13.1's claim that application-mysql.yml and schema-mysql.sql "are committed" was
not the only stale assertion this phase's own read-everything approach turned up. Reading every
package for the package-info sweep (7.2) found controller.webhook's package-info still reading
"Empty for now - PaymentWebhookController... arrive[s] in Phase 7", written before Phase 7
existed and never updated after it shipped that exact controller two phases ago. Both are the
same failure mode design doc 19's risk register names for a different context ("a design
document that describes code that does not exist"): a true-when-written sentence that nobody
revisits once the thing it described actually arrives. Fixed here alongside adding the three
genuinely missing package-info files (controller.admin, controller.advice, controller.user -
present since Phase 6, never given one).
What 7's final sweep otherwise turned up: TODO/FIXME/XXX, System.out/printStackTrace, and
Instant.now()/LocalDate.now() outside test support were all clean except the two Payment gaps
above (both addressed) and one grep false positive (Masker.java's own Javadoc example string
"411111XXXXXX1111", which is masked-card-number prose, not a marker comment). gradlew's
executable bit was genuinely broken (mode 100644, confirmed failing sh gradlew otherwise) and
is fixed (git update-index --chmod=+x gradlew, verified 100755 and a direct ./gradlew --version
run with no sh prefix).
Nothing in the resilience stack or elsewhere needed removing under design doc 19's "defend in
one sentence or remove it" rule - the bulkhead's one sentence is in the README's own key-
decisions section, and every other resilience component already carried a one-line
justification from earlier phases that this phase's read-through did not find reason to
dispute.
Nothing found in this phase is believed wrong for Phase 10. Phase 10's own scope
(Swagger/OpenAPI, AES field encryption, Docker) is explicitly gated on tests and README being
complete first, which this phase's own work is what makes true.

16.9 Phase 10 findings
The de-risking question first, because Phase 0 was burned by exactly this once already:
springdoc-openapi does exist for Spring Boot 4, unlike resilience4j-spring-boot4 (16.2). The
3.x line is the Boot 4 line - 3.1.1 was the current release - and its own POM depends on
spring-boot-tomcat, spring-boot-health and spring-boot-starter-webmvc-test, which are Boot 4
module names, so the compatibility question was answerable before writing a line of code. No
hand-written stand-in was needed this time.
It does, however, drag Jackson 2 onto a Jackson 3 classpath: swagger-core-jakarta 2.2.55 pulls
com.fasterxml.jackson.core 2.21.x, while this application runs on tools.jackson 3.1.5. Both
coexist without conflict only because Jackson 3's package rename is what it is - the two never
collide at the class level, and the shared jackson-annotations artifact means DTO annotations
are still read correctly by Swagger's schema generator. It is worth knowing rather than
discovering: adding API documentation to this project doubles the number of Jackson major
versions on its runtime classpath, and a future dependency conflict there would be confusing
without this note.
The real finding is that the document springdoc generates out of the box is wrong for this
application in two ways, both of which would break a generated client rather than merely
inconvenience a reader. First, every controller.admin/controller.user method returns a bare
DTO and ResponseEnvelopeAdvice adds the ApiResponse envelope afterwards, at a layer springdoc
cannot see - so POST /api/v1/user/bookings was documented as returning BookingResponse when a
client actually receives it nested under "data". Second, that same endpoint and the property
onboarding endpoint both genuinely return 201 but set it inside a ResponseEntity, which is
runtime code rather than metadata, so both were documented as plain 200s. The envelope is now
applied by an OperationCustomizer scoped to the same two base packages ResponseEnvelopeAdvice
itself is scoped to - deliberately not by annotating twenty controller methods, which would be
a second copy of "which endpoints are enveloped" free to drift from the first, the same class
of drift 16.8 recorded. The status codes were fixed at the source instead: both methods now
declare @ResponseStatus(HttpStatus.CREATED) and return the plain DTO, which is behaviourally
identical (verified live: still 201, still enveloped), introspectable, and incidentally matches
how every other controller in this codebase is already written. OpenApiDocumentTest asserts the
enveloped shape, the 201s, the deliberately-unenveloped webhook response and the documented
X-Role header, so none of it can quietly regress.
AES field encryption turned out to have a consequence worth recording, because it is not
obvious from the feature description: encrypting a column that an entity mapping depends on
makes the cipher a prerequisite of the persistence layer itself, not merely of the code that
reads a guest. Guest's PII columns carry @Convert, so Hibernate must construct
EncryptedStringConverter - with its FieldCipher dependency - before it can build the
EntityManagerFactory at all. The first full-suite run after wiring it up failed twenty tests
across six @DataJpaTest slices, almost none of which touch a Guest: BookingRepositoryTest,
DailyInventoryConstraintTest, AvailabilityFilterTest, SearchPropertyLocalDateTest,
IdempotencyRecordSweeperTest and RefundInvariantTest all failed to start their slice. Component
scanning covers the full application context but not a slice, which is what made this visible
only in tests. Resolved by grouping the three beans into FieldEncryptionConfig so each slice
needs one @Import rather than three plus an @EnableConfigurationProperties. A static holder
would have avoided the imports entirely and was rejected on principle: this project states
plainly that every singleton in it is a Spring-managed bean and never a hand-rolled
getInstance(), and a mutable static holding a cipher is precisely the shape that rule exists to
exclude. The imports are the honest, visible cost.
Two further encryption consequences, both accepted deliberately rather than worked around. The
columns had to be widened a long way - an encrypted value is roughly 4*ceil((28 + utf8Bytes)/3)
+ 3 characters once the 12-byte IV, 16-byte GCM tag, Base64 encoding and version prefix are
accounted for, so address went from VARCHAR(300) to VARCHAR(2048) and phone from VARCHAR(20) to
VARCHAR(256); GuestDetails' own @Size limits are what keep those bounds computable rather than
arbitrary. And date_of_birth stopped being a SQL DATE, becoming ciphertext in a VARCHAR: it can
no longer be range-queried or compared in SQL. Nothing in this system ever did either to that
column, which is exactly what makes encrypting it defensible and what would make the same
treatment indefensible on bookings.check_in. schema-mysql.sql was updated in the same change
rather than later, since a reference schema that no longer matches the entities is the specific
failure 16.8 had just finished fixing.
Encryption at rest and the 12.6.4 masking layer compose correctly, which was verified live
rather than assumed: a guest booked through the running application with real-looking details
comes back from the admin lookup as "A*** M****" / "a***@example.com" / "*****3210" - decrypted
correctly from storage by the converter, then masked on the way out by @Sensitive. The two
layers answer different questions (who can read the database file, versus who can read an API
response) and neither substitutes for the other. Erasure is a third, still-separate concern and
is unchanged: GuestRedactionService still really overwrites the plaintext, so crypto-shredding
remains rejected for the reason the README already gives.
Docker built and ran, verified rather than asserted: the image builds from the committed
multi-stage Dockerfile, the container starts, /actuator/health reports UP, the API and Swagger
UI both answer through it, Docker's own HEALTHCHECK transitions to healthy, and the process
runs as uid 10001 rather than root. One honest note on the process: the Docker daemon was not
running when this phase started, so it had to be started to verify any of this - which also
started several unrelated containers on the machine that had restart policies set. The image is
~580MB, almost all of it the Temurin JRE base plus the 65MB fat jar; a jlink runtime or Boot's
layered-jar extraction would shrink it, and neither was done because nothing here is waiting on
image size. No Compose file and no MySQL service were added, deliberately: 13.1's argument that
requiring a database to be stood up first is reviewer friction does not stop applying just
because there is now a container to put one in.
The most useful thing this phase found was not in anything it built: the full-suite run on a
machine now also running Docker and eight unrelated containers failed SweeperPaymentRaceTest,
which 16.8 had recorded as "not flaky" after fifteen clean iterations. The failure was a defect
in that test, not in the product, and the distinction matters. There are two legitimate ways
the payment thread can lose the sweeper race: it reads the booking while still payable, then
loses at commit (OptimisticLockingFailureException), or the sweeper commits EXPIRED before the
payment reads the booking at all, in which case requirePayableBookingState rejects it up front
with InvalidPaymentStateException. The test only tolerated the first and treated the second as
an unexpected failure. Every invariant it actually exists to protect - exactly one of
expired-or-confirmed, inventory released exactly once, never a CONFIRMED booking with released
inventory - held in the failing run. Outcome two is arguably the better product behaviour of
the two, failing fast with a precise domain error rather than doing work that is already
doomed. Fixed by accepting both losing paths and, while there, tightening the assertion in the
other direction: when the sweeper wins, the payment must have failed somehow, since a payment
silently succeeding against an EXPIRED booking is the corruption this test is really looking
for. Sixty further iterations across four forced re-runs pass. The lesson is the one 19's risk
register already states for deadlock ("load-dependent, so it is covered by a repeated-run
concurrency test") applied one level up: repeated runs on an idle machine explore a narrower
set of interleavings than the same test under real contention, and a race test that has only
ever passed can still be encoding an incomplete picture of what passing means.
An initial draft of the HEALTHCHECK in this phase was simply wrong - it invoked "java -e",
which is not a thing, and mixed exec-form CMD with shell redirection - and would have left
every container permanently unhealthy while the application underneath was fine. It was caught
by writing it out and reading it rather than by any tool, and replaced with a curl call against
the actuator endpoint the application already exposes (curl being the one package the runtime
stage installs). Recorded here because a health check that silently never passes is the kind of
defect that survives a long time: nothing fails, a dashboard just quietly stays red.
Nothing found in this phase is believed wrong for anything that follows it. Phase 10 was the
last of the planned phases; 17.1's "never cut" list is intact, and the three optional extras
this phase covers were the only outstanding items in 16.1.

17. Implementation Plan
Phase 1 — Entity core (revised mid-course, see 0.1)
Built initially as a framework-free domain model (value objects, typed ids, repository ports) per the original plan below, then reworked into the current shape: JPA entities directly (Booking + BookingLineItem per 3.4, plus placeholder aggregates for Property/RoomType/DailyInventory/Guest/Payment/LedgerEntry sufficient to give each Spring Data repository a concrete target), BookingState + BookingStateMachine as a static transition-table utility, the exception hierarchy, and one JpaRepository interface per aggregate. Unit tests for the FSM (full state-pair cartesian product), Booking's date-range validation, and Bean Validation constraints — plus, since persistence is no longer a separate later concern, a @DataJpaTest proving identity-generated ids, auto-assigned business uids and the uid uniqueness constraint actually work against embedded H2.
Phase 2 — Onboarding, inventory and pricing
Ownership hierarchy, PropertyOnboardingService, InventoryMaterializer, PricingStrategy implementations writing price_per_unit (4.2.1), reprice and rate-override admin endpoints, H2 schema with all constraints and indexes, seed fixture.
Phase 3 — Booking, reservation and locking (the differentiator)
InventoryReservationService with the atomic conditional UPDATE (5.2.1), sorted night ordering (5.3), all-or-nothing multi-night transaction, multi-unit decrement, BookingLineItem price snapshot, optional InventoryLockRegistry fast path, optimistic locking on booking state, BookingSweeper for hold expiry and completion (4.4).
Write the four concurrency tests here, not later — single-unit race, multi-unit race, multi-night atomicity, deadlock avoidance. They are the proof everything else rests on, and they are the tests most likely to expose a design error while there is still time to fix it.
Phase 4 — Payment, SPI, resilience
PaymentGatewayProvider + three mocks + router, circuit breaker with correct ignoreExceptions, timeout, idempotency-gated retry, per-provider bulkhead, PAYMENT_UNKNOWN fallback, and the stuck-transaction resolution of Section 7.6 — status-check ladder, payment_status_check records, inventory-hold expiry, MANUAL_REVIEW and its admin resolution endpoint.
Phase 5 — Cancellation, refund, reversal, ledger
Refund policy strategies, ReversalService, append-only ledger with the refund invariant, inventory release ordering.
Phase 6 — API layer
Envelopes, ResponseEnvelopeAdvice, GlobalExceptionHandler, error-code enum, admin/user/ webhook packages, Bean Validation, correlation-ID filter, apiType derivation interceptor.
Phase 7 — Webhooks and crypto
HMAC signer/verifier with constant-time compare, replay guard, webhook_event_log, outbound signed delivery with backoff, and the data-protection layer of Section 12.6 — @Sensitive annotation and serializer, log-appender backstop, payload redaction before audit persist, GuestRedactionService, and the leak / erasure / no-PII-in-audit tests.
Phase 8 — Search
Filter chain, all filters, availability filter last.
Phase 9 — Hardening
Remaining tests, README, MySQL reference profile.
Phase 10 — Optional
Swagger → AES field encryption → Docker.
17.1 Cut order under time pressure
Never cut: domain model with line items and multi-unit · FSM including hold expiry and COMPLETED · atomic conditional reservation · sorted night ordering · the four concurrency tests · property-local dates · idempotency layer (a) · refund policy strategy · README
Protect: the stuck-transaction path (7.6). It is the clearest payments-experience signal in the project and it is what makes the breaker, PAYMENT_UNKNOWN, reversals and the ledger one mechanism instead of four features. If time is short, build the ladder with three intervals instead of eight and keep MANUAL_REVIEW — a reduced ladder still demonstrates the design; removing it collapses the whole payments story.
Redaction (12.6) is also protected — it is a few classes, and for a payments-background candidate its absence would read as a gap rather than a scoping choice.
Cut in this order: Docker → Swagger → AES field encryption → bulkhead → pricing strategy → webhook_event_log → reversal scenarios 2 and 3 (keep scenario 1) → ledger → outbound webhooks
The ledger is the last thing cut from the payments block, but it is cuttable. Idempotency and refund policy are not — both are explicitly in the brief.

18. README Contents (deliverable)
How to build and run (./gradlew bootRun, H2 console URL, seed profile)
Sample cURL for every flow: onboard → search → book → pay → webhook → cancel
Architecture summary and dependency direction
Key design decisions and trade-offs — the section that carries the most weight:
Per-night inventory rows vs date ranges, and why
Eager materialisation with bounded horizon, and what lazy would cost
Atomic conditional UPDATE as the reservation mechanism — no read-then-write window; why this was chosen over SELECT ... FOR UPDATE; why daily_inventory has no version column; why the in-JVM lock is an optimisation and not the guarantee
The deadlock risk moved into the DB rather than disappearing — total ordering on (roomTypeId, stayDate) is what prevents it
All-or-nothing multi-night reservation via rollback, not compensating decrements
Multi-unit bookings, and why partial allocation is a correctness failure
Per-night pricing on the inventory row; line-item price snapshot so reprices never alter a settled booking
Hold expiry and the sweeper; why CONFIRMED → COMPLETED would otherwise be unreachable
Stay dates as property-local calendar dates, not instants
Concurrency control chosen per path (conditional statement / optimistic / unique constraint), with the contention reasoning — and why pessimistic locking appears nowhere
PAYMENT_UNKNOWN as a first-class state; why the fallback must not guess
Stuck-transaction resolution: bounded status-check ladder, the three independent decision points (inventory release / auto-reversal deadline / attempts exhausted), and MANUAL_REVIEW as a non-terminal parking state
Inventory released at T+15m while payment is still unresolved — inventory is perishable, money is recoverable
Three idempotency layers
Refund vs reversal distinction
Simplified append-only ledger, and why not double-entry
Inventory released before the refund call, and why
msgId as the single idempotency key, with a stated retention window; apiType derived server-side rather than client-supplied, and why the NPCI pattern does not transfer
Search-then-book is inherently racy; alternatives considered and why optimistic display with precise failure was chosen
SPI-style provider registry via Spring rather than ServiceLoader, and when ServiceLoader would be right
Personal data vs the immutable ledger: append-only tables hold opaque identifiers only, so erasure tombstones the guest row while the financial trail stays intact — crypto-shredding considered and why it was not needed
Stay dates + property constitute a location history — a by-product of the domain, not a collected field
Sensitive-payment-data handling: SAD never retained vs PAN unreadable; redact after HMAC verification; structural masking plus a log-appender backstop
Why volatile and atomic variables are almost absent, and why an atomic counter on inventory would be incorrect
Patterns deliberately excluded (Abstract Factory, hand-rolled Singleton, full Composite)
H2 by default with a committed MySQL reference profile
Bulkhead's justification under virtual threads
Assumptions
Production evolution — Section 16 verbatim
What would come next with more time

19. Risk Register
Risk
Mitigation
Infrastructure ambition crowds out domain work
Phases 1–3 are non-negotiable and come first. Section 17.1 cut order is fixed in advance.
Scope is large for 48 hours
Cut order decided before starting, not under pressure at hour 40.
Reviewer reads the resilience stack as over-engineering
Every non-brief component has a one-line justification in the README; Section 16 shows what was deliberately not built. The excluded list is as much of the argument as the included one.
@TransactionalEventListener misuse
AFTER_COMMIT phase only — never notify on a rolled-back booking.
Bulkhead cannot be justified under virtual threads
Justification is stated in 7.5 (backpressure, not thread conservation). If it cannot be defended in one sentence, it gets removed.
Ledger creep toward full accounting
Explicitly single-sided, four classes, one table. Boundary documented.
Stuck-transaction handling expands into a workflow engine
Scope fixed in 7.6.6: ladder, records, hold expiry, MANUAL_REVIEW, two tests. No SLA tracking, no alerting integration, no dispute flow.
Inventory-hold window tuned by guesswork
15m is a stated assumption, externalised as config, with the trade-off reasoning documented rather than presented as an optimum.
Sensitive data leaks through a hand-written log statement
Two layers (12.6.5): structural masking plus a log-appender backstop. Layer 2 exists precisely because layer 1 depends on discipline.
Personal data drifts into an append-only table, making erasure impossible
Single stated rule (12.6.3) plus a structural test asserting no @Sensitive field exists on any append-only entity. Cheap now, expensive to retrofit.
Redaction applied before HMAC verification, breaking signatures
Ordering fixed in 12.3 and covered by a dedicated test.
Reaching for volatile/atomics to look thorough
Position recorded in 5.7 with the reasoning. Absence is deliberate and defensible; misuse would be a correctness bug.
DB-level deadlock on concurrent multi-night bookings
The atomic UPDATE moved this risk from the JVM into the database rather than removing it (5.3). Total ordering on (roomTypeId, stayDate) plus bounded retry on deadlock/lock-timeout. Load-dependent, so it is covered by a repeated-run concurrency test, not a single-shot one.
Multi-unit partial allocation
All-or-nothing transaction (5.2.3) with rollback rather than compensating decrements. Covered by a dedicated test, since a single-unit test cannot detect it.
Price drift altering a settled booking
Line-item snapshot (3.4.1), deliberately denormalised, with an immutability test.
Inventory leaking on abandoned bookings
BookingSweeper (4.4). The sweeper-vs-in-flight-payment race is handled by the booking's optimistic lock and covered by a test.
Server-timezone bug on stay dates
Property ZoneId and injected Clock; no now() in domain code (4.5).
Scope exceeds the window
Accepted knowingly. Estimated 34–44 focused hours against 48 elapsed. Phases 1–3 are ordered first so that an incomplete submission is still a coherent one, and the cut order in 17.1 is fixed in advance rather than decided under pressure.


