# Hotel Booking Platform

A Spring Boot backend for a hotel booking platform: property onboarding, availability search,
booking, payment, and cancellation with refunds. Java 21, Spring Boot 4, H2 in memory, Gradle.

The core flows work end to end over REST and are covered by unit and concurrency tests. Where
this goes deeper than the brief asked — payment ambiguity, refunds and a ledger, webhooks,
field encryption — that is called out as such rather than presented as required scope; see
[Scope](#scope) below.

**Further reading:** [`DESIGN.md`](DESIGN.md) is the full design record and is what code
comments cite when they say "design doc 5.2.4". [`docs/DECISIONS.md`](docs/DECISIONS.md) is the
decisions-and-trade-offs write-up, [`docs/API.md`](docs/API.md) the full cURL walkthrough,
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) the diagrams, and
[`docs/OPERATIONS.md`](docs/OPERATIONS.md) the production-evolution notes, MySQL profile and Docker.

## Build and run

```
./gradlew bootRun
```

Requires nothing beyond a JDK — H2 is in memory and starts with the application. The app
listens on `:8080`.

```
./gradlew build          # compiles and runs the full test suite (268 tests)
```

**Demo seed data** — 10 properties across 6 cities, behind a profile so the default run starts
empty:

```
./gradlew bootRun --args='--spring.profiles.active=demo'
```

**Swagger UI** at [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html), raw document at
`/v3/api-docs`. **H2 console** at `/h2-console` (JDBC URL `jdbc:h2:mem:hotelbooking`, user `sa`,
blank password).

Docker and a MySQL reference profile exist but are not the primary path — see
[`docs/OPERATIONS.md`](docs/OPERATIONS.md).

## The five core flows

Every state-changing request carries an envelope (`msgId`/`timestamp`/`channel`/`version`/
`payload`) and gets one back (`msgId`/`correlationId`/`status`/`data`|`error`/`respondedAt`).
`msgId` is the idempotency key. Every request needs an `X-Role` header: `ADMIN` for
`/api/v1/admin/**`, `USER` for `/api/v1/user/**`.

| Flow | Endpoint |
|---|---|
| Onboard a property | `POST /api/v1/admin/properties` |
| Search | `POST /api/v1/user/properties/search` |
| Book | `POST /api/v1/user/bookings` |
| Pay | `POST /api/v1/user/bookings/{bookingUid}/pay` |
| Cancel | `POST /api/v1/user/bookings/{bookingUid}/cancel` |

[`docs/API.md`](docs/API.md) walks all five with real request/response pairs, plus a webhook
callback and two deliberate failure cases (sold-out inventory, double cancellation).

## How the brief's requirements are met

| Requirement | Where |
|---|---|
| Discovery with pluggable filters | `search` package. `SearchFilterChain` runs every `SearchFilter` bean in `order()`; a new filter is one `@Component` and no change anywhere else. Cheap in-memory predicates run before the two that read inventory. |
| Multi-property owner, single property as a special case | `Owner -> PropertyGroup -> Property -> RoomType -> DailyInventory`. Every property belongs to a group, including a group of exactly one for an independent hotel, so nothing branches on `if (isChain)`. |
| Booking with no double-booking | One `daily_inventory` row per `(room_type_id, stay_date)`; reservation is a single atomic conditional `UPDATE`, all-or-nothing across nights, with a `booked_units <= total_units` check constraint as the backstop. |
| Payment behind a common abstraction | `PaymentGatewayProvider` SPI + `PaymentGatewayRouter`; CARD, UPI and WALLET mock providers. Adding a provider is one `@Component`. Payment outcome drives the booking state. |
| Cancellation with a pluggable policy | `RefundPolicy` + `RefundPolicyFactory`, three policies, resolved per property group. Cancellation releases the held inventory. |
| Persistence behind interfaces | `repository` holds framework-free `*Store` ports; `repository.jpa` is the only package that names Spring Data or JPQL. |

All four bonus items are present: concurrency handling, a pluggable pricing strategy
(`PricingStrategy` with flat, weekend-surge and seasonal implementations), payment idempotency,
and OpenAPI.

## Architecture

> **Diagrams:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) carries four rendered plates — the
> layer stack and persistence seam, the reservation mechanism as a sequence, the full ER diagram
> and the booking state machine — plus every constraint and index with what it buys.

```
controller.admin / controller.user / controller.webhook   <- one package per role
controller.advice                                          <- envelope + exception handling
        |
      service                                               <- business logic
        |                           repository (ports: *Store)     gateway / webhook / crypto / security
        |
   repository.jpa (adapters)                                <- the only package naming Spring Data or JPQL
        |
      entity (JPA)                                          <- the schema's single source of truth
```

Dependency direction is one-way, top to bottom. `entity` holds the state machines
(`BookingStateMachine`, `PaymentStateMachine`, `RefundStateMachine`, `ReversalStateMachine`) as
static, table-driven classes alongside the data they constrain, rather than scattering
transition rules across the services that call `transitionTo`. `search` is its own top-level
package so its plugin structure is visible in the package tree.

The decisions behind this — the persistence seam, per-night inventory rows, the atomic
conditional `UPDATE`, eager materialisation and the rest — are written up in
[`docs/DECISIONS.md`](docs/DECISIONS.md).

## Scope

The brief asks for depth over breadth, and this repository is larger than the brief requires.
The core five flows are the deliverable; beyond them, the payment path is taken deliberately
deep (gateway failure, ambiguous outcomes, a status-check ladder, reconciliation, reversals and
a ledger), and webhooks, field-level encryption and PII redaction are also built.

That extra work is real and tested, but it is beyond what was asked. If you are reviewing
against the brief, the packages that answer it are `search`, `service`, `entity`, `repository`
and `gateway`; the rest is elaboration. [`docs/OPERATIONS.md`](docs/OPERATIONS.md) records what
is deliberately *not* built and where each piece would go in production.

## Assumptions

- **The 15-minute inventory-hold window is a tuned guess, not a measured optimum** (design doc
  19's own risk register entry). It is externalised as `booking.hold-ttl`, and the trade-off is
  stated rather than hidden: shorter loses more legitimate slow-payers to expiry, longer holds
  more abandoned inventory off sale.
- **The 24-hour idempotency retention** (`payment.idempotency.retention`, now enforced by
  `IdempotencyRecordSweeper`) must exceed the longest plausible client retry
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
- **Search is advisory, not a reservation** (see the search-then-book decision in
  [`docs/DECISIONS.md`](docs/DECISIONS.md) and the sold-out example in [`docs/API.md`](docs/API.md)): the room-night a search result names can be gone by the
  time booking is called, by design, and the booking path is the sole source of truth.

## What would come next with more time

- **Key management for field encryption.** The encryption itself is built, but its
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
  the test suite runs on H2 and the MySQL profile is a reference artefact, unexercised here.
- Everything in the [Production evolution](docs/OPERATIONS.md#production-evolution) table.
