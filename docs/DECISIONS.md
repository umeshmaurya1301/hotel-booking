# Key design decisions and trade-offs

Each of the following was a real choice with a losing alternative. Section numbers refer
to [`DESIGN.md`](../DESIGN.md). Back to the [README](../README.md).

### The persistence seam: ports and adapters, not `JpaRepository`

The brief asks that persistence stay "behind repository interfaces so it could be swapped
later". Extending `JpaRepository` directly — the obvious reading, and what this project did
until late in the build — satisfies that in letter only. The interface is then a *JPA* interface: its
query methods are Spring Data derived queries, its important statements are JPQL, and every
caller that touches it depends on JPA. Swapping the store would mean rewriting the interfaces,
not just the implementations, which is the one thing the seam existed to prevent.

So the layer is split in two:

- **`repository`** — one `*Store` port per aggregate (14 of them). Plain Java interfaces over
  domain types. Nothing here imports `org.springframework.data`.
- **`repository.jpa`** — for each port, a package-private Spring Data interface holding the
  queries and a package-private adapter implementing the port by delegating to it. The only
  package in the application that names a Spring Data type or contains a line of JPQL.
- **`JpaStores`** — one `@Configuration` binding all 14 ports to their adapters. Swapping a
  store is editing the line that names it; a partial migration is editing the lines you are
  moving.

The payoff is sharpest at `DailyInventoryStore.reserveUnits`, which is the correctness
mechanism of the whole system. As a port it states the *contract* — reserve-or-refuse, decided
atomically, answered by a row count where 0 means "not enough availability" — in terms a
document store or an in-memory store could also satisfy. The single conditional `UPDATE` that
currently honours it is one store's answer, documented on the adapter. That is the difference
between a guarantee the domain owns and a guarantee that happens to be true of the ORM in use.

`PropertyStore.findForSearchByCityNormalised` makes the same point from the other direction:
the port asks for search candidates with room types and amenities loaded, and the two-query
fetch strategy that avoids both N+1 and a Hibernate cartesian product lives entirely inside
`JpaPropertyStore`. The search service never knew about `MultipleBagFetchException`, and now it
structurally cannot.

**The honest limit.** The ports return the `@Entity` types rather than a separate set of
persistence-free domain objects mapped at the boundary. Entities here are already close to
plain domain objects — the state machines and invariants live on them, not in the services — so
a parallel model plus mappers would double the type count to remove an annotation. The ports
are store-agnostic; the types crossing them are still JPA-annotated. A genuinely non-relational
implementation would need that second step. This layer makes it possible; it does not take it.

**Cost paid:** 14 hand-written adapters, ~40 files, one extra call per persistence operation.
For a service this size that is a real tax, and worth naming rather than pretending the layer
is free.

### Other decisions

**Per-night inventory rows, not date ranges (4.1).** `daily_inventory` has one row per
`(room_type_id, stay_date)`, not a range table with overlap logic. A range representation makes
"is this room available for these five nights" a geometry problem (interval overlap, splitting
and merging ranges on partial bookings) instead of a single indexed row read per night. The
per-night row is more rows but every operation on it is O(1) and needs no interval algebra.

**Eager materialisation with a bounded horizon (4.2).** Every room type's inventory is written
for the next `inventory.horizon-days` (90) nights at onboarding time, not computed lazily on
first search. Lazy materialisation means the very first search or booking for a new night has
to detect "no row yet" and create one under contention — exactly the race the atomic UPDATE
reservation mechanism is built to avoid needing. Eager materialisation trades some idle storage
(nights nobody will ever book) for never having a code path that creates inventory under load.

**Atomic conditional UPDATE as the reservation mechanism (5.2.1).** A single statement —
`UPDATE daily_inventory SET booked_units = booked_units + :n WHERE room_type_id = :id AND
stay_date = :date AND booked_units + :n <= total_units` — both checks and reserves, so there is
no read-then-write window for two requests to race through. `SELECT ... FOR UPDATE` was the
alternative: it would work, but it takes a row lock for the whole application-level
check-then-write instead of one statement, holding it longer than necessary and needing the
database to serialize readers that a plain conditional UPDATE does not have to block at all.
`daily_inventory` has no `@Version` column because there is no read-then-write window for
optimistic locking to protect — a version column nothing reads would be dead weight. The
in-JVM lock (`InventoryLockRegistry`, `inventory.lock.enabled`) is a throughput optimisation
that serialises same-key contenders before they reach the database; a test
(`ReservationWithoutInJvmLockTest`) proves turning it off cannot cause overbooking, because the
conditional UPDATE — not the lock — is the actual guarantee.

**The deadlock risk moved into the database, not removed (5.3).** Locking one row per night
inside a transaction means a multi-night booking holds several row locks until commit; two
bookings taking overlapping ranges from opposite ends can deadlock. This is handled by always
taking nights in ascending `(room_type_id, stay_date)` order, making circular wait structurally
impossible, with a bounded, jittered retry (`BookingService`) as the secondary defence for
whatever else might interleave badly. `ReservationOrderingTest` pins the ordering directly, and
`InventoryReservationConcurrencyTest.overlappingRangesBookedFromOppositeEndsAllComplete` proves
it under real concurrent load, since a load-dependent race never shows up single-threaded.

**All-or-nothing multi-night reservation via rollback (5.2.3).** A three-night booking issues
three UPDATEs in one transaction; if the second returns zero rows, the exception rolls the
whole transaction back, releasing the first night automatically. The alternative — reserve each
night as it succeeds, and write compensating decrements if a later night fails — needs code
that runs exactly once even under a crash between the failure and the compensation, and is a
second thing that can be wrong. Rollback is a mechanism the database already guarantees, so
there is no compensating-decrement code and therefore no compensating-decrement bug.

**Multi-unit bookings, and why partial allocation is a correctness failure (5.2.2).** A
2-unit request against 3 free units must reserve exactly 2 or fail, never 1 (a partial booking
nobody asked for) and never let two concurrent 2-unit requests both succeed against a 3-unit
room (4 units allocated against 3 that exist). The conditional UPDATE's `booked_units + :n <=
total_units` check makes this atomic per request regardless of `:n`, but a single-unit test
cannot detect either failure mode — `concurrentMultiUnitRequestsNeverPartiallyAllocate` exists
specifically because the naive "check then reserve one at a time" implementation looks correct
under a single-unit test and is not.

**Per-night pricing with a line-item snapshot (4.2.1 / 5.2).** `daily_inventory.price_per_unit`
is written at materialisation time (so weekend/seasonal/override pricing is expressible per
night), but a booking's `booking_line_items` copy that price at booking time and never read it
again. Reading the live price at settlement time would mean a `PricingStrategy` or an admin
override changing what an already-confirmed guest owes — `repricingAfterBookingDoesNotChangeWhatTheBookingOwes`
is the test that would fail if this snapshot were ever removed.

**Hold expiry and the sweeper (4.4).** A `CREATED` or `PENDING_PAYMENT` booking holds its
room-nights for `booking.hold-ttl` (15m) before `BookingSweeper` releases them; without this,
every abandoned booking (someone who never pays) takes those nights off sale forever. The
sweeper's second job — `CONFIRMED -> COMPLETED` past checkout — is smaller but not optional:
that transition is in `BookingStateMachine`'s table and nothing else in the system can reach
it. An unreachable state in a state machine is a defect, not an unused feature, and
`aConfirmedStayPastCheckoutIsCompleted` is the test that would catch its removal.

**Stay dates as property-local calendar dates, never instants (4.5).** "The night of the 14th"
means the 14th in the property's own IANA zone, not the server's — every date computation
(materialisation horizon, hold expiry semantics, completion, search's availability check) reads
`LocalDate.now(clock.withZone(property.zone()))`, never the server's own date. A property whose
local date has already rolled over relative to the server (or vice versa) is the exact scenario
`theHorizonStartsAtThePropertysLocalToday_notTheServers` and `SearchPropertyLocalDateTest`
construct on purpose, using two properties in different zones and one instant that is a
different calendar day in each.

**Concurrency control chosen per path, not one mechanism everywhere (5.2.4).** Three different
mechanisms for three different shapes of contention: the atomic conditional UPDATE for
inventory (a counter with a ceiling — no read-then-write window to protect), JPA optimistic
locking (`@Version`) for `Booking`/`Payment`/`Refund`/`Reversal` (rare true conflicts, e.g. the
sweeper racing a settling payment — see `SweeperPaymentRaceTest` — where retrying the loser is
cheap and correct), and a database unique constraint for idempotency keys and webhook dedup
(the constraint itself serialises two concurrent inserts, not application code). Pessimistic
locking (`SELECT ... FOR UPDATE`) appears nowhere: every contended path here has a cheaper,
more specific mechanism, and reaching for a table lock by default would serialise work that
does not need to be serialised.

**`PAYMENT_UNKNOWN` as a first-class state; the fallback must never guess (7.1 / 7.2).** A
circuit-breaker-open call or a gateway timeout means the outcome was never observed — money may
or may not have moved. The system resolves this to `UNKNOWN`, never `CONFIRMED` (which could
be wrong if the charge never went through) and never `FAILED` (which could be wrong if it did).
Guessing either way is a worse failure mode than an honest "we don't know yet, and here is how
we will find out" — `PaymentCircuitBreakerTest`'s `PaymentService`-level assertion pins exactly
this: a forced-open breaker produces `UNKNOWN`, nothing else.

**Stuck-transaction resolution: three independent decision points (7.6.2).** The inventory-
hold-window release (does the room stay held), the bounded status-check ladder (do we keep
asking the gateway), and the auto-reversal deadline (when do we presume failure and stop
asking) are deliberately not conflated into one timer — each answers a different question on
its own clock. `MANUAL_REVIEW` is the ladder's bounded exit: a parking state with a human in
the loop once every configured interval has been tried with no resolution, not a silent dead
end. `StuckTransactionResolutionTest` drives all three paths for real: late settlement into a
reversal, ladder exhaustion into `MANUAL_REVIEW` with both admin resolution outcomes, and the
inventory-release-while-still-unresolved case.

**Inventory released at T+15m while the payment is still unresolved (7.6.1).** The room goes
back on sale before the payment is known to have failed, because inventory is perishable
(an unsold room-night is gone forever) and money is recoverable (a reversal can always undo a
late charge). The alternative — hold the room until the payment is fully resolved, however long
that takes — protects against a rare late-success case at the cost of a room that could have
been sold to someone else for certain. `StuckTransactionResolutionTest`'s combined scenario
proves this is real: a second guest genuinely books the released room-night while the first
payment is still `UNKNOWN`, and the first payment's late `SETTLED` response, arriving after
that release, reverses rather than confirms.

**Three idempotency layers (design doc 8).** Layer (a), client → API, is `msgId` on every
state-changing request, checked by `IdempotencyService` before any business logic runs. Layer
(b), API → gateway, is a `providerReference` generated once per payment attempt and reused on
every retry — a fresh reference per retry is the standard double-charge bug. Layer (c),
provider → API (webhook), is the `(provider_code, event_id)` unique constraint plus an
FSM-level no-op guard in `PaymentSettlementService`, because a provider can legitimately send
two different `eventId`s for the same settlement, so the constraint alone is not enough.
`PaymentIdempotencyApiTest` is layer (b)'s proof: the same `msgId` posted twice against the
`/pay` endpoint charges the gateway exactly once, verified on the provider mock's own
interaction count, not just on the ledger row count.

**Refund vs. reversal (9.1).** A refund is guest-initiated, policy-applied, and can be partial
(`CancellationService` + `RefundPolicy`). A reversal is always full, never policy-applied, and
undoes a charge that should not have stood — late settlement after an expired hold, a duplicate
charge, or an admin manual correction. Conflating them would mean a `RefundPolicy` deciding how
much of an erroneous duplicate charge to give back, which is a business question that does not
exist for that case.

**A simplified, single-sided ledger, not double-entry (9.4).** `LedgerEntry` records amount,
direction and type per booking, with the invariant `sum(REFUND) + sum(REVERSAL) <=
sum(CHARGE)` checked before any debit is written — no chart of accounts, no counterparty
account, no trial balance. There is no bank statement in this exercise's scope to reconcile a
double-entry ledger against, and building one would misrepresent how much accounting this
system actually needs. `RefundInvariantTest` proves the invariant is a real running total
(two partial refunds that together exceed the charge), not a single-comparison check.

**Inventory released before the gateway refund call, and why (9.5).** Cancellation's step
ordering releases the room-night first, then calls the gateway — the room becomes bookable
again immediately, and a subsequent refund failure is a money-reconciliation problem, not a
reason to keep the room blocked for a guest who is no longer staying. `CancellationOrderingTest`
pins this with an `InOrder` mock verification, and confirms a gateway refund failure still
leaves the release standing — a design that held the room hostage to gateway health would be
strictly worse for the one resource that cannot be un-sold later.

**`msgId` as the single idempotency key; `apiType` derived server-side (11.3, 8a).** There is
one idempotency key, not two (`msgId`, not a separate `Idempotency-Key` header) — the request
envelope makes it required by construction. `apiType` is stamped by `ApiTypeInterceptor` reading
an `@Api` annotation on the matched controller method, never accepted as a client field: in a
REST API the route already identifies the operation, so asking the client to restate it would
duplicate information the URL already carries. This is a deliberate departure from the
NPCI/UPI message-type-field pattern this design was informed by, whose constraint (many message
kinds sharing one endpoint) does not exist here.

**Search-then-book is inherently racy; a stale result fails precisely (10, and see
[Assumptions](../README.md#assumptions)).** `PropertySearchService` reads `daily_inventory` without taking
any lock, so its answer can be stale by the time a client calls booking. Optimistic display
with a precise, informative failure at booking time — the same `InventoryUnavailableException`
booking always throws, naming the room type and night — was chosen over a soft hold on search
(reserving units for a short TTL before checkout). A soft hold adds a second expiring-
reservation mechanism alongside the booking hold TTL, complicates the sweeper, and holds real
inventory hostage to abandoned searches; the cost is not justified by what it buys.

**An SPI-style provider registry via Spring, not `ServiceLoader` (6.1/6.2).** Every
`PaymentGatewayProvider` is a `@Component`; `PaymentGatewayRouter` collects all of them via
constructor injection and picks one by `supports(method, bankCode)`. Adding a provider is
registering one more bean — the router does not change. `ServiceLoader` would be the right
choice for a plugin loaded from an external jar with no DI container available; inside a Spring
application, letting the container that is already managing every other bean's lifecycle manage
these too is simpler and gets configuration injection for free.

**Personal data vs. the immutable ledger (12.6.3).** Append-only tables (`ledger_entries`,
`payment_status_checks`, `webhook_event_log`) hold only opaque identifiers — `bookingId`,
`paymentId` — never a guest's name, email, phone or address. `Guest` carries the PII, referenced
by id only, so erasure (`GuestRedactionService`) can tombstone one row (`redactedAt` set,
fields nulled) without touching booking or financial history, which by construction never held
that data to begin with. Crypto-shredding (encrypting PII with a per-guest key, then discarding
the key to "erase" it) was considered and not needed: real deletion of a genuinely isolated row
is simpler and gives a stronger guarantee than a key-destruction scheme adds.

**Field-level encryption at rest for guest PII, and why it is not crypto-shredding (12.6).**
`Guest`'s `fullName`, `email`, `phone`, `address` and `dateOfBirth` are encrypted per column
with AES-GCM (`EncryptedStringConverter` / `EncryptedLocalDateConverter`), so a stolen database
file or backup does not hand over identities in the clear. GCM rather than CBC or ECB, for two
concrete reasons: it is authenticated, so a row edited underneath the application fails to
decrypt instead of yielding plausible garbage; and a random IV per value means two guests with
the same name do not produce the same ciphertext, so the column does not leak equality while
appearing to hide values. This is a *different* concern from erasure and does not replace it —
`GuestRedactionService` still really overwrites the plaintext with a tombstone, and
crypto-shredding (encrypting per guest, then discarding that guest's key to "erase" them)
remains rejected for the reason given below. Applied per field, never with `autoApply`:
encrypting every string in the schema would put ciphertext in `booking_uid` and
`city_normalised`, which are exactly the columns things look up by value. `guestUid` and
`redactedAt` are deliberately left in the clear so lookups still work and "this guest was
erased" stays auditable without decrypting anything. Two real costs, both accepted knowingly:
the columns had to be widened substantially (ciphertext is ~4/3 of plaintext plus IV, tag and
Base64 overhead), and `dateOfBirth` stops being a SQL `DATE` — a capability nothing in this
system used for that column, and the reason the same treatment would *not* be defensible on
`bookings.check_in`.

**Stay dates + property are a location history — a by-product, not a collected field
(12.6.1).** Nowhere does this system have a "guest location" field; the fact that a booking
names a property and a date range is enough to reconstruct where a guest was and when. This is
named explicitly as a privacy-relevant by-product of the domain rather than left implicit,
because a reviewer thinking about data protection would ask about it regardless of whether the
system calls it "location data".

**Sensitive-payment-data handling (12.6.2/12.6.4/12.6.5/12.6.6).** Full card/instrument data
(SAD — sensitive authentication data, e.g. CVV) is never retained anywhere, not even redacted;
PAN-shaped fields are masked to their last four digits before anything persists them. Redaction
happens *after* HMAC signature verification, never before — redacting first would change the
bytes the signature was computed over and break every legitimate callback (`HmacVerifyThenRedactOrderingTest`
covers this ordering directly). Two independent layers exist rather than one:
`@Sensitive` + a custom Jackson serializer for structural masking on any DTO field so tagged,
and a Logback converter (`LogRedactionConverter`) as a backstop that pattern-matches PAN/CVV
shapes in whatever text actually reaches a log line — layer two exists precisely because layer
one depends on every field being correctly annotated, and a backstop that depends on nothing
being remembered is what catches the field someone forgot to tag.

**Why `volatile`/`atomic` types are almost absent, and why an atomic counter on inventory would
be wrong (5.7).** `InventoryLockRegistry` and `PaymentCircuitBreaker`'s window are the two
places any concurrency primitive beyond a `synchronized` block or the database's own locking
appears, and both are deliberate, narrow exceptions with a stated reason (a lock table keyed by
inventory key; a sliding window of recent outcomes). An `AtomicInteger` for `booked_units`
would be actively wrong: it would only be correct within one JVM, and this system's actual
correctness boundary is the database row, shared across every instance that might ever run.
Reaching for `volatile`/atomic types to "look thorough" was a named risk (19) precisely because
it is easy to add and easy to get subtly wrong when the real boundary is a row lock, not memory
visibility.

**Patterns deliberately excluded: Abstract Factory, hand-rolled Singleton, full Composite.**
`RefundPolicyFactory` and `PricingStrategyRegistry` are Factory/Strategy, not Abstract Factory —
there is one family of related objects to create, not families-of-families. Every "singleton"
in this codebase is a Spring-managed bean, never a hand-rolled `getInstance()` — the container
already owns that lifecycle question. The `Owner -> PropertyGroup -> Property -> RoomType`
hierarchy is a fixed-depth tree, not a general recursive Composite, because nothing in this
domain nests a `Property` inside another `Property`; modelling it as full Composite would add a
uniform interface for an operation (e.g. "total capacity") that only one level of the tree ever
actually needs.

**H2 by default, with a committed MySQL reference profile (13.1).** `./gradlew bootRun` must
work with nothing installed — reviewer friction is the real risk to a project like this being
evaluated fairly, and requiring a running MySQL instance before the first request can be made
would be exactly that friction. `application-mysql.yml` and `schema-mysql.sql` (see below) make
the MySQL-specific knowledge (dialect, connection pool sizing, partitioning DDL) visible without
imposing that setup cost on anyone who does not opt into it.

**The bulkhead's justification under virtual threads (7.5, and 19's own rule: "if it cannot be
defended in one sentence, it gets removed").** One sentence: `@ConcurrencyLimit` on each mock
provider bounds *concurrent in-flight calls to that specific provider* so one degraded provider
cannot starve capacity meant for another, which is backpressure on an external dependency, not
thread conservation — virtual threads make threads cheap, they do not make a slow downstream
dependency fast, and unbounded concurrent calls to a struggling provider would still be the
wrong thing to do regardless of how cheap the calling thread was.
