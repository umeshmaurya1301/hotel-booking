# Architecture and schema

Diagrams for the hotel booking service. Every box, column and constraint below is read out of the
code in this repository, not sketched from an intended design — the JPA annotations are the single
source of truth for the schema (`ddl-auto: create-drop` generates it at startup), so a table here
that disagrees with `src/main/java/com/umesh/hotelbooking/entity` is a bug in this file.

Diagrams are [Mermaid](https://mermaid.js.org) and render inline on GitHub. A styled standalone
version of the same four plates is in [`schematics.html`](schematics.html) — open it directly in a
browser, no build step.

| | |
|---|---|
| Tables | 16 |
| Persistence ports | 14 |
| Booking states | 10 (4 terminal) |
| Overbooking defences | 3 |

---

## 1. Layers, and the line technology cannot cross

Dependencies run one way, top to bottom. The persistence seam is the load-bearing boundary:
everything above `repository` is written in domain terms, and `repository.jpa` is the only package
in the application that names a Spring Data type or contains a line of JPQL.

```mermaid
flowchart TB
    subgraph edge["HTTP edge"]
        direction LR
        CU["controller.user<br/>search · book · pay · cancel"]
        CA["controller.admin<br/>onboard · inventory · ledger"]
        CW["controller.webhook<br/>HMAC-signed callbacks"]
        ADV["controller.advice + web<br/>envelope · ErrorCode to status · correlation id"]
    end

    subgraph domain["Domain — service and search"]
        direction LR
        BS["BookingService"]
        PS["PaymentService"]
        CS["CancellationService<br/>RefundPolicy x3"]
        IRS["InventoryReservationService<br/>reserve / release"]
        SFC["SearchFilterChain<br/>7 SearchFilter beans"]
    end

    subgraph outbound["Outbound and cross-cutting"]
        direction LR
        GW["gateway<br/>PaymentGatewayProvider<br/>Card · UPI · Wallet mocks<br/>circuit breaker · bulkhead"]
        SEC["crypto · webhook · security"]
    end

    subgraph ports["repository — PORTS · no org.springframework.data above this line"]
        P["14 x Store interfaces, plain Java over domain types<br/>BookingStore · DailyInventoryStore · PaymentStore · PropertyStore · ..."]
    end

    subgraph adapters["repository.jpa — ADAPTERS"]
        direction LR
        AD["Jpa Store adapters x14<br/>package-private, thin delegation"]
        SD["Jpa Repository interfaces x14<br/>Spring Data · the only JPQL"]
    end

    ENT["entity — JPA entities + state machines"]
    DB[("H2 in-memory by default<br/>MySQL reference profile")]

    edge -->|"validated request envelope"| domain
    domain -->|"calls ports only"| ports
    domain -.->|"mocked behind our own interface"| outbound
    ports -->|"JpaStores binds port to adapter"| adapters
    AD --> SD
    adapters --> ENT
    ENT --> DB
```

**What this buys.** Moving off JPA means replacing the two boxes in `repository.jpa` and the
bindings in `JpaStores`. The services, filters and controllers above the seam never named a
persistence type, so they do not change. `PersistenceSeamTest` fails the build if that stops being
true — it also asserts the adapters stay package-private, so nothing outside can inject one.

---

## 2. How a room-night is taken exactly once

Booking is a check-then-act, and check-then-act is where double-booking lives. The fix is not a lock
around the sequence — it is removing the sequence. Predicate and mutation are the same statement,
evaluated under the row lock the database already takes to perform the write, so there is no window
for a competing transaction to read stale availability and act on it.

```mermaid
sequenceDiagram
    autonumber
    participant BC as BookingCreator
    participant IRS as InventoryReservationService
    participant LR as InventoryLockRegistry
    participant ST as DailyInventoryStore port
    participant AD as JpaDailyInventoryStore
    participant DB as daily_inventory

    BC->>IRS: reserve(roomTypeId, nights, units)
    Note over IRS: nights sorted ascending —<br/>no circular wait, no deadlock
    IRS->>LR: acquireAll(keys)
    Note over LR: DEFENCE 1 · throughput only.<br/>In-JVM, switchable. Not correctness.

    loop each night, in one transaction
        IRS->>ST: reserveUnits(id, night, n)
        Note over ST: contract: reserve-or-refuse,<br/>atomic, answered by a count
        ST->>AD: delegate
        AD->>DB: UPDATE daily_inventory SET booked_units = booked_units + :n<br/>WHERE room_type_id = :id AND stay_date = :d<br/>AND booked_units + :n ≤ total_units
        Note over DB: DEFENCE 2 · correctness.<br/>DEFENCE 3 · CHECK booked_units ≤ total_units
        DB-->>AD: row count
        AD-->>IRS: 1 taken / 0 unavailable
    end

    alt any night returns 0
        IRS-->>BC: InventoryUnavailableException
        Note over BC,DB: transaction rolls back —<br/>nights already taken are released by the rollback,<br/>so there is no compensating decrement to get wrong
    else all nights returned 1
        IRS-->>BC: reserved
    end
```

**Why not `SELECT ... FOR UPDATE` then write.** Two round trips instead of one; it holds the row lock
across the application's decision rather than for the duration of a statement; and it puts
correctness in the hands of code that could forget to take the lock.

**The row count is the answer.** Nothing re-reads to "confirm". The port defines 0 as "not enough
availability", the conditional `UPDATE` produces exactly that, and the adapter forwards it untouched
— one place decides whether a room-night was taken, and it is the statement itself.

---

## 3. Database schema

Sixteen tables in three groups: the supply side an owner onboards, the demand side a guest creates,
and the money trail every booking leaves. `idempotency_records` and `webhook_event_log` reference
nothing — both must survive a request that never created a booking.

**Read the line style.** A solid line is a real foreign key constraint. A dotted line is a plain
`Long` id column carrying no database-level FK. Only six edges are solid: the ownership spine, plus
a booking's line items. That is deliberate — the reservation path updates `daily_inventory` by
`room_type_id` without loading a `RoomType` entity, and the money tables are append-oriented records
that outlive what they describe.

```mermaid
erDiagram
    owners ||--o{ property_groups : "owns"
    property_groups ||--o{ properties : "owns"
    properties ||--o{ room_types : "owns"
    properties ||--o{ property_amenities : "owns"
    bookings ||--o{ booking_line_items : "owns"

    room_types ||..o{ daily_inventory : "referenced by id"
    guests ||..o{ bookings : "referenced by id"
    properties ||..o{ bookings : "referenced by id"
    room_types ||..o{ bookings : "referenced by id"
    bookings ||..o{ payments : "referenced by id"
    payments ||..o{ refunds : "referenced by id"
    payments ||..o{ reversals : "referenced by id"
    payments ||..o{ ledger_entries : "referenced by id"
    payments ||..o{ payment_status_checks : "referenced by id"

    owners {
        bigint id PK
        varchar owner_uid UK
        varchar name
        varchar email
    }
    property_groups {
        bigint id PK
        varchar property_group_uid UK
        bigint owner_id FK
        varchar settlement_bank_code
        varchar refund_policy_code
    }
    properties {
        bigint id PK
        varchar property_uid UK
        bigint property_group_id FK
        varchar city_normalised
        int star_rating
        varchar zone_id
        varchar currency
    }
    room_types {
        bigint id PK
        varchar room_type_uid UK
        bigint property_id FK
        int total_units
        int max_guests
        decimal base_price_per_night
    }
    daily_inventory {
        bigint id PK
        bigint room_type_id UK
        date stay_date UK
        int total_units
        int booked_units
        decimal price_per_unit
    }
    property_amenities {
        bigint property_id FK
        varchar amenity
    }
    guests {
        bigint id PK
        varchar guest_uid UK
        varchar full_name
        varchar email
        varchar phone
        varchar date_of_birth
        timestamp redacted_at
    }
    bookings {
        bigint id PK
        varchar booking_uid UK
        bigint guest_id
        bigint property_id
        bigint room_type_id
        date check_in
        date check_out
        int units
        decimal total_amount
        enum state
        timestamp hold_expires_at
        bigint version
    }
    booking_line_items {
        bigint id PK
        bigint booking_id FK
        date stay_date
        int units
        decimal price_per_unit
        decimal line_total
    }
    payments {
        bigint id PK
        varchar payment_uid UK
        bigint booking_id
        enum method
        varchar provider_reference UK
        decimal amount
        enum state
        int attempt_no
        timestamp next_attempt_at
        boolean inventory_released
        bigint version
    }
    refunds {
        bigint id PK
        varchar refund_uid UK
        bigint booking_id
        bigint payment_id
        decimal amount
        varchar policy_code
        enum state
        bigint version
    }
    reversals {
        bigint id PK
        varchar reversal_uid UK
        bigint booking_id
        bigint payment_id
        enum reason
        decimal amount
        enum state
    }
    ledger_entries {
        bigint id PK
        varchar ledger_entry_uid UK
        bigint booking_id
        bigint payment_id
        enum type
        decimal amount
        enum direction
        timestamp occurred_at
        varchar correlation_id
    }
    payment_status_checks {
        bigint id PK
        bigint payment_id
        int attempt_no
        timestamp checked_at
        enum gateway_status
        varchar correlation_id
    }
    idempotency_records {
        varchar msg_id PK
        varchar request_hash
        enum status
        text response_body
        timestamp created_at
        enum api_type
    }
    webhook_event_log {
        bigint id PK
        varchar provider_code UK
        varchar event_id UK
        boolean signature_valid
        enum outcome
        timestamp received_at
    }
```

Every money table also carries `booking_id`; only the payment edge is drawn, to keep the diagram
readable. The five `guests` columns `full_name`, `email`, `phone`, `address` and `date_of_birth` are
**encrypted at rest** (AES-GCM via `EncryptedStringConverter` / `EncryptedLocalDateConverter`),
which is why they are wider `varchar` than their plaintext would need.

### Constraints and indexes

Indexes make things fast and can be added later. The constraints are different: each is a rule the
application would otherwise have to be trusted to remember.

| Name | Table | Kind | What it buys |
|---|---|---|---|
| `ck_not_overbooked` | `daily_inventory` | CHECK | `booked_units <= total_units`. The last line of defence against a double-booking, and the only one no application bug can route around. |
| `uq_inventory_slot` | `daily_inventory` | UNIQUE | `(room_type_id, stay_date)`. Makes "one row per room-night" structural, so the horizon materialiser cannot create a duplicate night under load. |
| `ck_non_negative` | `daily_inventory` | CHECK | `booked_units >= 0`. A double release is a bug worth surfacing; silently driving the counter negative would be worse. |
| `ck_price_positive` | `daily_inventory` | CHECK | `price_per_unit > 0`. No pricing strategy can write a free night. |
| `uq_payment_provider_reference` | `payments` | UNIQUE | The reference is stable across retries, so a retried initiate cannot become a second charge. |
| `uq_webhook_event` | `webhook_event_log` | UNIQUE | `(provider_code, event_id)`. Gateways retry callbacks; a replayed settlement must be recorded once and applied once. |
| `msg_id` primary key | `idempotency_records` | PK | Two concurrent requests carrying the same message id race for this key. The loser finds out at insert time, not at commit — before it has taken payment. |
| `version` | `bookings`, `payments`, `refunds`, `reversals` | OPT-LOCK | Optimistic locking on the mutable aggregates, so a sweeper and a user request cannot write a state transition over each other. |
| `idx_inventory_lookup` | `daily_inventory` | INDEX | `(room_type_id, stay_date)` — the reservation and availability read path. |
| `idx_property_city_rating` | `properties` | INDEX | `(city_normalised, star_rating)` — search matches the normalised column, never display casing. |
| `idx_payment_due` | `payments` | INDEX | `(state, next_attempt_at)` — finds payments due for the next rung of the status-check ladder without scanning. |
| `idx_ledger_booking` | `ledger_entries` | INDEX | `(booking_id, occurred_at)` — the per-booking money trail, in order. |
| `idx_status_check_due` | `payment_status_checks` | INDEX | `(gateway_status, checked_at)` — the reconciliation scan. |
| `idx_idempotency_created_at` | `idempotency_records` | INDEX | Lets the sweeper bulk-delete expired records in one statement instead of loading a growing table into memory. |

---

## 4. Booking lifecycle

Ten states, held in a table-driven state machine (`BookingStateMachine`) on the entity rather than
scattered across the services that call `transitionTo`. Payment outcome drives booking state.

```mermaid
stateDiagram-v2
    [*] --> CREATED : book

    CREATED --> PENDING_PAYMENT : pay initiated
    CREATED --> EXPIRED : hold TTL (15m) lapses

    PENDING_PAYMENT --> CONFIRMED : gateway settled
    PENDING_PAYMENT --> PAYMENT_FAILED : gateway declined
    PENDING_PAYMENT --> PAYMENT_UNKNOWN : timeout / breaker open
    PENDING_PAYMENT --> EXPIRED : hold lapses

    PAYMENT_UNKNOWN --> CONFIRMED : status ladder finds success
    PAYMENT_UNKNOWN --> PAYMENT_FAILED : status ladder finds failure
    PAYMENT_UNKNOWN --> MANUAL_REVIEW : ladder exhausted
    PAYMENT_UNKNOWN --> REVERSED : late success on expired hold

    MANUAL_REVIEW --> CONFIRMED : operator resolves
    MANUAL_REVIEW --> PAYMENT_FAILED : operator resolves
    MANUAL_REVIEW --> REVERSED : operator reverses

    CONFIRMED --> COMPLETED : stay ends
    CONFIRMED --> CANCELLED : guest cancels, RefundPolicy applied
    CONFIRMED --> REVERSED : charge undone

    PAYMENT_FAILED --> EXPIRED : hold released

    CANCELLED --> [*]
    COMPLETED --> [*]
    EXPIRED --> [*]
    REVERSED --> [*]
```

A timeout parks the booking in `PAYMENT_UNKNOWN` rather than guessing an outcome; an escalating
status-check ladder resolves it, or hands it to `MANUAL_REVIEW` when the ladder runs out. A late
success against an already-expired hold is *reversed* rather than honoured — which is why `REVERSED`
exists as a distinct terminal state instead of being folded into `CANCELLED`. A refund is
policy-applied and partial; a reversal is always full.

Re-entering the current state is a deliberate no-op rather than an error: gateways retry webhooks, so
a duplicate `PAYMENT_SUCCESS` re-applying `CONFIRMED -> CONFIRMED` must be silent.

---

## Keeping these honest

These diagrams have no generator — they are hand-maintained, and that is a real maintenance cost
worth naming. What limits the drift:

- The schema is generated from the JPA annotations, so `src/main/java/com/umesh/hotelbooking/entity`
  is the thing to diff this file against; there is no separate DDL to also keep in sync.
- `PersistenceSeamTest` enforces the one structural claim in diagram 1 that a reader would otherwise
  have to take on trust.
- `BookingStateMachineTest` pins the transition table drawn in diagram 4.
