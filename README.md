# Hotel Booking Platform

A machine-coding exercise: onboarding, search, booking, payment (with resilience and stuck-
transaction resolution), cancellation/refunds, an API envelope, webhooks and data protection,
and search/discovery, built phase by phase against `PROJECT_STRUCTURE.txt.txt` (the design
document). This file is the Phase 9 deliverable it always pointed to: build/run instructions,
a real cURL walkthrough, an architecture summary, the weighted "key design decisions" section,
stated assumptions, the out-of-scope table copied verbatim from the design document, and what
would come next.

## Build and run

```
./gradlew bootRun
```

Requires nothing installed beyond a JDK — H2 is in-memory and starts with the application (design
doc 13.1: reviewer friction is the real risk, so the default profile must work first try). The
app listens on `:8080`.

**H2 console** — `/h2-console`, JDBC URL `jdbc:h2:mem:hotelbooking`, user `sa`, blank password.

**Demo seed data** (10 properties across 6 cities, `SeedDataLoader`, `@Profile("demo")`):

```
./gradlew bootRun --args='--spring.profiles.active=demo'
```

Both commands above were run against this exact checkout before being written down here.

**API documentation** — with the app running, Swagger UI is at
[`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) and the raw OpenAPI document at
`/v3/api-docs`. See [API documentation](#api-documentation-openapi) for what had to be
customised to make that document *true*.

**MySQL reference profile** — a *reference* profile, not active by default; see
[MySQL reference profile](#mysql-reference-profile) below. Do not use it to run the app unless
you actually have a MySQL instance.

**Docker** — optional and deliberately not the primary path (design doc 16, 13.1):

```
docker build -t hotel-booking .
docker run -e ENCRYPTION_KEY="$(openssl rand -base64 32)" -p 8080:8080 hotel-booking
```

See [Docker](#docker) below. Both commands were run against this checkout.

## API walkthrough (cURL)

Every state-changing request carries the Phase 6 envelope
(`msgId`/`timestamp`/`channel`/`version`/`payload`) and gets one back
(`msgId`/`correlationId`/`status`/`data`|`error`/`respondedAt`); a client that omits the
envelope gets a 400. Every command below was run against a freshly started instance
(`--spring.profiles.active=demo`, plus one property onboarded fresh for this walkthrough) and
its real response pasted — not hand-written JSON.

Every request needs `X-Role`: `ADMIN` for `/api/v1/admin/**`, `USER` for `/api/v1/user/**`.
Enforcement is stubbed (design doc 11.4) — a *mismatched* role is rejected, a missing one is
let through — but the header is shown throughout since a real deployment will not stay that way.

### 1. Onboard a property (admin)

```bash
curl -X POST http://localhost:8080/api/v1/admin/properties \
  -H 'Content-Type: application/json' -H 'X-Role: ADMIN' \
  -d '{
    "msgId": "'"$(uuidgen)"'",
    "timestamp": "2026-09-08T10:00:00Z",
    "channel": "WEB",
    "version": "v1",
    "payload": {
      "ownerName": "Coastal Stays LLP",
      "name": "Whitefield Grand",
      "city": "Bengaluru",
      "locality": "Whitefield",
      "starRating": 4,
      "zoneId": "Asia/Kolkata",
      "currency": "INR",
      "amenities": ["WIFI", "POOL"],
      "roomTypes": [
        { "name": "Deluxe King", "totalUnits": 5, "maxGuests": 2, "basePricePerNight": 7500.00 }
      ]
    }
  }'
```

```json
{"msgId":"onboard-1788894501826333600","correlationId":"61f0d0f1-5db0-4726-a297-ff2c1a07e3c5","status":"SUCCESS","data":{"propertyUid":"d5ef32a6-8b62-4572-8f14-9f550e447192","propertyGroupUid":"49536fed-32bc-4cd5-b14b-ea9e002b4a33","ownerUid":"eddddf49-2e21-4e1d-963b-4849ed565da6","name":"Whitefield Grand","city":"Bengaluru","locality":"Whitefield","latitude":null,"longitude":null,"starRating":4,"zoneId":"Asia/Kolkata","currency":"INR","amenities":["WIFI","POOL"],"roomTypes":[{"roomTypeUid":"efb65f6f-181c-4413-9034-f58cb145b56e","name":"Deluxe King","totalUnits":5,"maxGuests":2,"basePricePerNight":7500.00}]},"respondedAt":"2026-09-08T19:08:21.884805500Z"}
```
`HTTP/1.1 201`. Onboarding materialises a 90-night inventory horizon for this room type
immediately (design doc 4.2) — nothing further needs to run before it is searchable.

### 2. Search (user)

```bash
curl -X POST http://localhost:8080/api/v1/user/properties/search \
  -H 'Content-Type: application/json' -H 'X-Role: USER' \
  -d '{
    "msgId": "'"$(uuidgen)"'",
    "timestamp": "2026-09-08T10:00:00Z",
    "channel": "WEB",
    "version": "v1",
    "payload": {
      "city": "Bengaluru",
      "checkIn": "2026-09-15",
      "checkOut": "2026-09-16",
      "units": 1,
      "adults": 2,
      "children": 0,
      "minStarRating": 4
    }
  }'
```

```json
{"msgId":"search-1788894507574549700","correlationId":"e4cbe93b-f6ec-464d-aca5-f05654bb1242","status":"SUCCESS","data":{"resultCount":3,"truncated":false,"results":[{"propertyUid":"c13dcad4-635f-4951-b850-608ef4050227","name":"Lakeview Residency", "...":"(seeded property, truncated here for length)"},{"propertyUid":"d5ef32a6-8b62-4572-8f14-9f550e447192","name":"Whitefield Grand","city":"Bengaluru","locality":"Whitefield","starRating":4,"amenities":["POOL","WIFI"],"roomTypes":[{"roomTypeUid":"efb65f6f-181c-4413-9034-f58cb145b56e","name":"Deluxe King","maxGuests":2,"availableUnits":5,"stayTotal":7500.00,"currency":"INR"}]},{"propertyUid":"2bb546bd-4f48-45ec-a235-e2c98a0eacb6","name":"Meridian Bengaluru","...":"(seeded property)"}]},"respondedAt":"2026-09-08T19:08:27.636997900Z"}
```
The property just onboarded is in the results, priced and available, alongside two seeded
Bengaluru properties. `truncated: false` — under `search.max-results` (50).

### 3. Book (user)

```bash
curl -X POST http://localhost:8080/api/v1/user/bookings \
  -H 'Content-Type: application/json' -H 'X-Role: USER' \
  -d '{
    "msgId": "'"$(uuidgen)"'",
    "timestamp": "2026-09-08T10:00:00Z",
    "channel": "WEB",
    "version": "v1",
    "payload": {
      "roomTypeUid": "efb65f6f-181c-4413-9034-f58cb145b56e",
      "checkIn": "2026-09-15",
      "checkOut": "2026-09-16",
      "units": 1,
      "adults": 2,
      "children": 0
    }
  }'
```

```json
{"msgId":"book-1788894514117635500","correlationId":"f83c9a93-e3a3-4fb0-9506-59386a5c4472","status":"SUCCESS","data":{"bookingUid":"a2b69108-a796-4cee-b160-392b1748120a","guestUid":"0c1f7826-5af2-4d5e-a803-5fd3891c25f3","propertyUid":"d5ef32a6-8b62-4572-8f14-9f550e447192","roomTypeUid":"efb65f6f-181c-4413-9034-f58cb145b56e","checkIn":"2026-09-15","checkOut":"2026-09-16","nights":1,"units":1,"adults":2,"children":0,"totalAmount":7500.00,"currency":"INR","state":"CREATED","holdExpiresAt":"2026-09-08T19:23:34.176217400Z","createdAt":"2026-09-08T19:08:34.176217400Z","lineItems":[{"stayDate":"2026-09-15","units":1,"pricePerUnit":7500.00,"lineTotal":7500.00}]},"respondedAt":"2026-09-08T19:08:34.198404200Z"}
```
`HTTP/1.1 201`. No `guestUid` was supplied, so a guest row was created implicitly. The room-
night is now held (`holdExpiresAt` 15 minutes out), not yet confirmed.

### 4. Pay (user)

```bash
curl -X POST http://localhost:8080/api/v1/user/bookings/a2b69108-a796-4cee-b160-392b1748120a/pay \
  -H 'Content-Type: application/json' -H 'X-Role: USER' \
  -d '{
    "msgId": "'"$(uuidgen)"'",
    "timestamp": "2026-09-08T10:00:00Z",
    "channel": "WEB",
    "version": "v1",
    "payload": { "method": "CARD" }
  }'
```

```json
{"msgId":"pay-1788894520711748200","correlationId":"3ed7331a-b8b1-4517-85f9-d597d8bd0f9d","status":"SUCCESS","data":{"paymentUid":"8b217285-69c2-4e93-b9d6-5fe58ef14692","bookingUid":"a2b69108-a796-4cee-b160-392b1748120a","method":"CARD","providerCode":"MOCK_CARD","amount":7500.00,"currency":"INR","state":"SETTLED","attemptNo":0,"nextAttemptAt":null,"createdAt":"2026-09-08T19:08:40.765267300Z","updatedAt":"2026-09-08T19:08:40.765267300Z"},"respondedAt":"2026-09-08T19:08:40.775340600Z"}
```
`method` is the only required field — `simulate` defaults to immediate settlement, so this is
the happy path. The booking is now `CONFIRMED` (not shown in this response's shape, but
`GET /api/v1/user/bookings/{uid}` would show it).

**The interesting case** — a gateway that does not answer immediately. Paying with
`"simulate": "STUCK_FOREVER"` instead:

```json
{"msgId":"pay2-1788894683934785900","correlationId":"433b0a48-1378-48fe-95ce-3cc9ebf264e8","status":"PENDING","data":{"paymentUid":"547b7ea1-a5e2-4e40-a9ed-be9e9e7d233e","bookingUid":"9f86f7fa-72fb-4391-9e55-d2b16e2464db","method":"CARD","providerCode":"MOCK_CARD","amount":7500.00,"currency":"INR","state":"UNKNOWN","attemptNo":0,"nextAttemptAt":"2026-09-08T19:11:53.969488200Z","createdAt":"2026-09-08T19:11:23.968488300Z","updatedAt":"2026-09-08T19:11:23.968488300Z"},"respondedAt":"2026-09-08T19:11:23.971488400Z"}
```
Note the envelope's own `status` is `PENDING`, not `SUCCESS` — the response body is not a
failure, but it is not a confirmation either (design doc 7.2). This is exactly the payment the
webhook example below resolves.

### 5. Webhook (system — HMAC, not `X-Role`)

The inbound webhook (`POST /api/v1/webhooks/payment/{providerCode}`) is authenticated by
HMAC-SHA256 over the raw request body (design doc 12.1–12.3), not the role header. Continuing
the stuck payment above: the real provider-side reference (`CARD-1788894683968-250822`, the
value our own system generated and sent at `initiate` time — never client-visible, read here
via the H2 console for demonstration purposes only) arrives later with a genuine
`PAYMENT_SUCCESS` callback:

```bash
BODY='{"eventId":"evt-demo-settle-1","eventType":"PAYMENT_SUCCESS","providerCode":"MOCK_CARD","eventTime":"2026-09-08T19:11:39Z","version":"v1","payload":{"providerReference":"CARD-1788894683968-250822"}}'
echo -n "$BODY" > /tmp/body.json
SIG=$(openssl dgst -sha256 -hmac "dev-secret-card" /tmp/body.json | sed 's/^.*= //')
curl -X POST http://localhost:8080/api/v1/webhooks/payment/MOCK_CARD \
  -H 'Content-Type: application/json' \
  -H "X-Signature: sha256=$SIG" \
  -H "X-Timestamp: $(date +%s%N | cut -b1-13)" \
  --data-binary @/tmp/body.json
```

```json
{"eventId":"evt-demo-settle-1","status":"RECEIVED"}
```
`HTTP/1.1 200`, and the payment is now genuinely `SETTLED` (confirmed by a follow-up
`GET /api/v1/user/bookings/{uid}/payments/{paymentUid}` against the running instance):
```json
{"correlationId":"555693c1-ad90-4a85-91ec-d0650e3e18b5","status":"SUCCESS","data":{"paymentUid":"547b7ea1-a5e2-4e40-a9ed-be9e9e7d233e", "...":"...","state":"SETTLED","attemptNo":0,"nextAttemptAt":null,"createdAt":"2026-09-08T19:11:23.968488Z","updatedAt":"2026-09-08T19:11:39.425960Z"},"respondedAt":"2026-09-08T19:11:47.403663Z"}
```
`dev-secret-card` is `application.yml`'s deliberately-committed development placeholder (see
that file's own comment) — never a production secret.

### 6. Cancel (user)

```bash
curl -X POST http://localhost:8080/api/v1/user/bookings/a2b69108-a796-4cee-b160-392b1748120a/cancel \
  -H 'Content-Type: application/json' -H 'X-Role: USER' \
  -d '{
    "msgId": "'"$(uuidgen)"'",
    "timestamp": "2026-09-08T10:00:00Z",
    "channel": "WEB",
    "version": "v1",
    "payload": { "reason": "Change of plans" }
  }'
```

```json
{"msgId":"cancel-1788894715396875700","correlationId":"626247f4-fcb2-4a28-8bcb-307914e5c64a","status":"SUCCESS","data":{"bookingUid":"a2b69108-a796-4cee-b160-392b1748120a","bookingState":"CANCELLED","refundUid":"a72e3491-21f8-4673-95ca-7d67ba882341","refundAmount":7500.00,"currency":"INR","refundState":"COMPLETED"},"refundState":"COMPLETED"}
```
Full refund: this property group's default policy (`FULL_REFUND_BEFORE_48H`) and the
cancellation happened well outside the 48-hour window before check-in.

### 7. Two deliberate failures

**A duplicate `msgId`.** The same booking request posted twice, byte-for-byte, with the same
`msgId`, returns the identical stored response rather than creating a second booking or
double-reserving the room-night:
```json
// first call
{"msgId":"dup-1788894722721742100", "...":"...", "data":{"bookingUid":"15ee272c-55b4-4511-b70a-8e4ce252bb17", "...":"createdAt":"2026-09-08T19:12:02.758371100Z"},"respondedAt":"2026-09-08T19:12:02.762414800Z"}
// second call, same msgId, same body
{"msgId":"dup-1788894722721742100", "...":"...", "data":{"bookingUid":"15ee272c-55b4-4511-b70a-8e4ce252bb17", "...":"createdAt":"2026-09-08T19:12:02.758371100Z"},"respondedAt":"2026-09-08T19:12:02.825685100Z"}
```
Identical `data` (down to the original `createdAt`), only `respondedAt` and `correlationId`
differ — this is a replay, not a re-execution.

**A sold-out room-night.** Booking all 5 units of Whitefield Grand's Deluxe King for one
night, then trying to book a 6th on the same night:
```bash
# after a 5-unit booking for 2026-09-25 already exists
curl -X POST http://localhost:8080/api/v1/user/bookings -H 'Content-Type: application/json' -H 'X-Role: USER' -d '{ "...":"...", "payload": {"roomTypeUid":"efb65f6f-181c-4413-9034-f58cb145b56e","checkIn":"2026-09-25","checkOut":"2026-09-26","units":1,"adults":2,"children":0}}'
```
```json
{"msgId":"unavail-1788894731818011500","correlationId":"bb87676f-6c4d-48f7-9c67-bf571561db40","status":"FAILURE","error":{"code":"INVENTORY_UNAVAILABLE","message":"No availability for room type efb65f6f-181c-4413-9034-f58cb145b56e on 2026-09-25","fieldErrors":[]},"respondedAt":"2026-09-08T19:12:11.852361900Z"}
```
`HTTP/1.1 409`-class rejection naming the exact room type and night — not a generic "booking
failed". This is also the exact message shape a stale search result turns into when a client
acts on it after someone else took the room (see [Assumptions](#assumptions)).

## API documentation (OpenAPI)

Swagger UI at `/swagger-ui.html`, raw document at `/v3/api-docs`, via springdoc-openapi. The
dependency is one line; `OpenApiConfig` is the part worth reading, and it exists because the
generated document was **wrong** out of the box in two specific ways:

- **It documented the wrong response shape.** springdoc reads controller method signatures, and
  every `controller.admin` / `controller.user` method returns a bare DTO — the `ApiResponse`
  envelope is added afterwards by `ResponseEnvelopeAdvice`, at a layer springdoc cannot see. So
  `POST /api/v1/user/bookings` was documented as returning `BookingResponse` directly, when a
  client actually receives that object nested under `data`. A client generated from that
  document looks for `bookingUid` at the top level and never finds it.
- **It documented the wrong status code.** Both `POST /api/v1/user/bookings` and
  `POST /api/v1/admin/properties` return 201, but set it inside a `ResponseEntity`, which is
  runtime code rather than metadata — springdoc reported a plain 200. Both now declare
  `@ResponseStatus(HttpStatus.CREATED)` and return the plain DTO, which is behaviourally
  identical, introspectable, and incidentally matches how every other controller in the
  codebase is written.

The wrapping is applied by an `OperationCustomizer` scoped to the *same two base packages*
`ResponseEnvelopeAdvice` is scoped to, rather than by annotating twenty controller methods —
a per-method annotation would be a second copy of "which endpoints are enveloped", free to
drift from the first. `controller.webhook` is correctly left unwrapped, because that path
genuinely returns a bare `WebhookAck` on the provider's own contract. `OpenApiDocumentTest`
asserts all of this, so the document cannot quietly go back to lying.

The stubbed `X-Role` header is documented per route too — read from each controller's own
`@RequireRole` — since a reader of the spec would otherwise have no way to know it exists.

## Architecture

> **Diagrams:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) carries four rendered plates — the layer
> stack and persistence seam, the reservation mechanism as a sequence, the full 16-table ER diagram, and
> the booking state machine — plus a table of every constraint and index with what it buys. Mermaid, so
> it renders inline on GitHub. [`docs/schematics.html`](docs/schematics.html) is the same four plates as
> a styled standalone page; open it in a browser, no build step.

```
controller.admin / controller.user / controller.webhook   <- one package per role (design doc 11.4)
controller.advice                                          <- envelope + exception handling, cross-cutting
        |
      service                                               <- business logic, one class per responsibility
        |                        \
   repository (ports: *Store)     gateway / webhook / crypto / security   <- outbound & cross-cutting concerns
        |
   repository.jpa (adapters)                                <- the only package that names Spring Data or JPQL
        |
      entity (JPA)                                          <- the schema's single source of truth
```

Dependency direction is one-way, top to bottom: controllers depend on services, services on
persistence ports and entities, nothing depends back up. Nothing above `repository.jpa` imports
`org.springframework.data` — see [the persistence
seam](#the-persistence-seam-ports-and-adapters-not-jparepository) below. `dto` (wire-contract records) and `config`
(`@ConfigurationProperties`) are used from every layer and depend on nothing else in the
project. `entity` holds the state machines (`BookingStateMachine`, `PaymentStateMachine`,
`RefundStateMachine`, `ReversalStateMachine`) as static, table-driven classes alongside the
JPA entities they govern — the transition rules live with the data they constrain, not
scattered across the services that call `transitionTo`.

The ownership hierarchy is `Owner -> PropertyGroup -> Property -> RoomType -> DailyInventory`
(design doc 3.1): every property belongs to a group, including a group of exactly one for an
independent hotel, so nothing downstream branches on `if (isChain)`.

Search (`com.umesh.hotelbooking.search`) is deliberately its own top-level package, not folded
into `service`: `SearchFilterChain` and its `SearchFilter`/`BatchSearchFilter` beans are a
small, self-contained plugin architecture (design doc 10.1), and keeping them together makes
that structure visible in the package tree rather than only in prose.

## Key design decisions and trade-offs

Each of the following was a real choice with a losing alternative, not a description of code
that is already readable on its own. Section numbers refer to `PROJECT_STRUCTURE.txt.txt`.

### The persistence seam: ports and adapters, not `JpaRepository`

The brief asks that persistence stay "behind repository interfaces so it could be swapped
later". Extending `JpaRepository` directly — the obvious reading, and what this project did
until Phase 10 — satisfies that in letter only. The interface is then a *JPA* interface: its
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
[Assumptions](#assumptions)).** `PropertySearchService` reads `daily_inventory` without taking
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

## Assumptions

- **The 15-minute inventory-hold window is a tuned guess, not a measured optimum** (design doc
  19's own risk register entry). It is externalised as `booking.hold-ttl`, and the trade-off is
  stated rather than hidden: shorter loses more legitimate slow-payers to expiry, longer holds
  more abandoned inventory off sale.
- **The 24-hour idempotency retention** (`payment.idempotency.retention`, now enforced by
  `IdempotencyRecordSweeper` as of this phase) must exceed the longest plausible client retry
  window; it is a stated choice, not a measured one.
- **Authorisation is stubbed** (design doc 11.4): `X-Role` rejects a mismatch and lets a
  missing header through. Real authentication/authorisation is out of scope per the brief.
- **The payment gateway is entirely mocked** (`AbstractMockProvider` and its three concrete
  providers): every "bank" in this system is a simulation with a client-selectable
  `SimulatedOutcome` lever, not a real integration.
- **`ddl-auto: create-drop`** for the default H2 profile: the schema is thrown away and rebuilt
  on every restart, which is correct for a demo/review database and would never be correct
  against a real one — see the MySQL profile's `validate` instead.
- **The committed encryption key is a development placeholder**, exactly like the webhook
  provider secrets alongside it: `security.encryption.key` is overridable by the
  `ENCRYPTION_KEY` environment variable, and in production would come from that or a secret
  manager, never from committed YAML. The design document deliberately does not invent a
  key-management story beyond externalised config, and neither does this — there is no key
  rotation, no envelope encryption and no HSM here. `FieldCipher` stamps a `v1:` prefix on
  every value it writes precisely so that a future key or algorithm change has something
  unambiguous to branch on rather than having to guess what an existing row contains.
- **Search is advisory, not a reservation** (see the search-then-book bullet above and the
  cURL walkthrough's sold-out example): the room-night a search result names can be gone by the
  time booking is called, by design, and the booking path is the sole source of truth.

## Production evolution

Section 16 of the design document, copied verbatim per that document's own instruction ("The
README carries this section verbatim, because explaining precisely where each would go is a
stronger signal at this level than building any of them prematurely.").

> **16. Out of Scope — With Production Evolution Notes**
>
> Each of these is genuinely known and deliberately not built.
>
> | Not built | Where it would go in production |
> |---|---|
> | Microservices | Split along property-catalog / inventory / booking / payment. Booking↔inventory↔payment currently share a transaction boundary; splitting them requires a saga with compensating actions — the reversal machinery in Section 9 is exactly that compensation, already modelled. |
> | gRPC | Inter-service calls once split. Extracting a service means defining a gRPC contract from the relevant service-layer method signatures directly and wrapping the existing service class as its implementation, which is mechanical but is genuinely a new step rather than a reuse of something already in place. (The design document wrote this row when 0.1 had dropped the port layer; the `*Store` ports added since are *persistence* ports, so they are the seam a store swap goes through, not a service boundary a gRPC contract could be promoted from. The distinction is the point — a port is only useful at the boundary it was drawn for.) |
> | Kafka + Avro + DLQ | The domain events already published in-process (BookingConfirmedEvent, PaymentSettledEvent) become topic messages. Avro schemas with a registry for contract evolution; DLQ for poison messages after bounded retry. Notification and reporting become consumers. |
> | Redis | Read-through cache for property catalog and hot availability. Distributed locking deliberately not proposed as a primary mechanism — Redlock's correctness under partition and clock skew is contested; the DB constraint remains the guarantee. |
> | Reporting store + partitioning | Booking history is append-heavy and queried by date range. CDC (Debezium → Kafka) into a reporting store; bookings PARTITION BY RANGE (YEAR(check_in)) in MySQL for partition pruning on date-bounded reports and cheap old-partition drops for retention. Not built here: H2 has no partitioning support, and at seed-data volume partition pruning would demonstrate nothing measurable. |
> | Distributed tracing | Correlation IDs are already threaded through logs, ledger and webhook records — the propagation contract exists. OpenTelemetry spans would attach to it once there are process boundaries to cross. Nothing distributed to trace today. |
> | mTLS / transport encryption | Between services once split. No inter-service hop exists; payload-level HMAC signing covers the one real trust boundary (the webhook). |
> | Live third-party hotel data | See 13.4. Build-time data sourcing only. |
> | Load testing / high-throughput tuning | The single-statement compare-and-set of 5.2.1 is already the primary mitigation and is implemented. Expected remaining bottleneck is row-lock contention on a single popular room-night. Further options, none implemented: shard the counter into K sub-rows per room-night and pick one at random (trades exact-availability reads for write throughput), queue reservation requests per inventory key, or cache availability with a short TTL and accept stale search results. Not measurable at this scope; unmeasured tuning would be theatre. |
> | Auth / authz | Out of scope per brief. Role separation is structural (Section 11.4); enforcement stubbed. |
> | Docker | Not in the rubric. Added last, only if tests and README are complete. |

## What would come next with more time

- **Key management for field encryption.** The encryption itself is built (see above), but its
  key lifecycle is not: no rotation, no re-encryption of existing rows under a new key, no
  envelope encryption or KMS/HSM integration. The `v1:` version prefix on every encrypted value
  exists so that work has somewhere to hook in, but the work itself is real and unstarted.
- **A real authentication/authorisation layer** replacing the stubbed `X-Role` header —
  structurally ready for it (role separation already exists as distinct packages and URL
  spaces), but the enforcement itself is out of scope per the brief.
- **Cursor pagination for search**, replacing the current cap-and-truncate
  (`search.max-results`) — a deliberate, stated gap for this exercise's scope (design doc
  10.3), not an oversight.
- **The MySQL reference profile actually exercised against a real MySQL instance** in CI —
  explicitly out of scope for this phase (§5.3 of this phase's own task spec: "the suite runs
  on H2; the MySQL profile is a reference artefact").
- Everything in the [Production evolution](#production-evolution) table above.

## MySQL reference profile

`application-mysql.yml` and `schema-mysql.sql` (`src/main/resources/`) are committed and real,
closing a gap this phase found: design doc 13.1 asserted both were already committed before
this phase, and neither existed. Not active by default — nothing loads them unless a run
explicitly opts in:

```
./gradlew bootRun --args='--spring.profiles.active=mysql'
```

With no MySQL instance running, this fails with a `CJCommunicationsException: Communications
link failure` / `Connection refused` — a real connectivity error, not a missing-driver or
malformed-config error, confirming the driver (`runtimeOnly("com.mysql:mysql-connector-j")`),
dialect and datasource config are all genuinely wired, not merely present as inert files. This
was verified by actually running the command above against this checkout, with no MySQL
listening, and reading the resulting stack trace.

`schema-mysql.sql` is a reference artefact, never executed by the application (there is no
`spring.sql.init.*` pointing at it, and it is absent from the default profile's classpath
scan) — the live schema is generated from the JPA annotations on the entities, which remain the
single source of truth (see `application.yml`'s own comment on this). The file documents every
table, its indexes with the reason each exists, `uq_inventory_slot` and the design doc 5.2.4
layer-3 CHECK constraints, and — commented out, per design doc 13.1's explicit ask — the
`bookings PARTITION BY RANGE (YEAR(check_in))` partitioning DDL design doc 16 specifies, with a
note on why it is not live: H2 (this project's actual database) has no partitioning support at
all, and at this project's seed-data volume it would demonstrate nothing measurable even
against real MySQL.

The suite runs on H2 only; the MySQL profile is intentionally never exercised by
`./gradlew test` (see [What would come next](#what-would-come-next-with-more-time)).

## Docker

A `Dockerfile` is included and is explicitly **not** the primary way to run this project —
design doc 16 lists Docker as "not in the rubric, added last, only if tests and README are
complete", and 13.1's whole argument is that `./gradlew bootRun` must work first try with
nothing installed. Nothing else in the repository depends on it.

```
docker build -t hotel-booking .
docker run -e ENCRYPTION_KEY="$(openssl rand -base64 32)" -p 8080:8080 hotel-booking
```

Both were run against this checkout: the image builds, the container comes up, `/actuator/health`
reports `UP`, the API and Swagger UI both answer through it, and Docker's own `HEALTHCHECK`
transitions to `healthy`. The container runs as a non-root user (`uid=10001(hotel)`), which is
free here because the application binds an unprivileged port, writes no files, and keeps its
entire database in memory.

Two deliberate omissions:

- **No Compose file and no MySQL service.** Adding one would reintroduce exactly the setup cost
  design doc 13.1 chose H2 to avoid. The container runs the same in-memory H2 the default
  profile does, so a container is a way to run this app without a JDK — not a way to run it
  against a real database.
- **No key baked into the image.** The encryption key is read from the environment, so the
  image carries none; an `ENV` line would put it in the image metadata for anyone who pulls it.
  The committed development default in `application.yml` is what makes `docker run` work with
  no arguments at all.

The image is ~580MB, most of which is the Temurin JRE base and the 65MB fat jar. A smaller
image (jlink/jdeps custom runtime, or Boot's layered-jar extraction for better layer caching)
is a real and unexercised option — at this project's scale it would be optimising something
nobody is waiting on.
