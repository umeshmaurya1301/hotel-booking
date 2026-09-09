-- Hotel Booking Platform — MySQL 8 reference schema.
--
-- THIS FILE IS NEVER EXECUTED. It is not on Spring's `schema-locations` and nothing loads it
-- at startup (see application-mysql.yml, which sets `ddl-auto: validate`, and application.yml,
-- which has no mysql profile active by default). The live schema — for the H2 profile this
-- application actually runs against — is generated from the JPA annotations on the entities in
-- `com.umesh.hotelbooking.entity` (and `com.umesh.hotelbooking.webhook.WebhookEventLog`); those
-- annotations are the single source of truth for every constraint and index in this project.
--
-- This file exists only to make the MySQL-specific knowledge (types, engine, partitioning)
-- visible to a reviewer without imposing the setup cost of standing up a real MySQL instance —
-- design doc 13.1's argument for treating MySQL as a reference profile, not an active one.
--
-- Derived from the entities as they exist as of Phase 9, by hand rather than by a committed
-- generator setting (design doc 13.1's own instruction): a generated-and-forgotten file drifts
-- from the code the moment an entity changes, and 13.1 already flagged one such drift — this
-- file and application-mysql.yml were both documented as "committed" before either existed.
--
-- Column types follow Hibernate's own MySQL8Dialect defaults for the corresponding Java type:
-- Long id -> BIGINT, String(n) -> VARCHAR(n), BigDecimal(p,s) -> DECIMAL(p,s), LocalDate ->
-- DATE, Instant -> DATETIME(6) (microsecond precision — Instant carries nanoseconds, but MySQL
-- DATETIME tops out at 6 fractional digits), boolean -> BOOLEAN (MySQL's alias for TINYINT(1)),
-- @Enumerated(STRING) -> VARCHAR(<declared length>).

-- =====================================================================================
-- owners — the account at the top of the ownership hierarchy (design doc 3.1):
-- Owner -> PropertyGroup -> Property -> RoomType -> DailyInventory. Not a guest, so the
-- erasure machinery of 12.6.3 does not apply to it.
-- =====================================================================================
CREATE TABLE owners (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uid  VARCHAR(36)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    email      VARCHAR(320),
    CONSTRAINT uq_owner_uid UNIQUE (owner_uid)
) ENGINE = InnoDB;

-- =====================================================================================
-- property_groups — a collection of properties under one owner (design doc 3.1). The
-- structural answer to "a single hotel is not a special case": every property belongs to a
-- group, even a group of exactly one.
-- =====================================================================================
CREATE TABLE property_groups (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_group_uid    VARCHAR(36)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    owner_id              BIGINT       NOT NULL,
    settlement_bank_code  VARCHAR(40),
    refund_policy_code    VARCHAR(40),
    CONSTRAINT uq_property_group_uid UNIQUE (property_group_uid),
    CONSTRAINT fk_property_group_owner FOREIGN KEY (owner_id) REFERENCES owners (id)
) ENGINE = InnoDB;

-- =====================================================================================
-- properties — one hotel. zone_id is load-bearing, not descriptive: every "what night is it
-- here" question is answered in this zone, never the server's (design doc 4.5).
-- =====================================================================================
CREATE TABLE properties (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_uid     VARCHAR(36)   NOT NULL,
    property_group_id BIGINT       NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    city             VARCHAR(120)  NOT NULL,
    city_normalised  VARCHAR(120)  NOT NULL,
    locality         VARCHAR(160),
    latitude         DECIMAL(9,6),
    longitude        DECIMAL(9,6),
    star_rating      INT           NOT NULL,
    zone_id          VARCHAR(60)   NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    CONSTRAINT uq_property_uid UNIQUE (property_uid),
    CONSTRAINT fk_property_group FOREIGN KEY (property_group_id) REFERENCES property_groups (id),
    -- Search's candidate fetch (task spec 8, PropertySearchService) filters on
    -- (city_normalised, star_rating) together; this index is what keeps that a single
    -- indexed lookup rather than a city-wide scan followed by a rating filter.
    INDEX idx_property_city_rating (city_normalised, star_rating)
) ENGINE = InnoDB;

-- =====================================================================================
-- property_amenities — the @ElementCollection backing Property.amenities. A join table, not
-- a column, so a property can advertise any subset of the enum without a schema change.
-- =====================================================================================
CREATE TABLE property_amenities (
    property_id BIGINT      NOT NULL,
    amenity     VARCHAR(40) NOT NULL,
    CONSTRAINT fk_property_amenity_property FOREIGN KEY (property_id) REFERENCES properties (id)
) ENGINE = InnoDB;

-- =====================================================================================
-- room_types — a sellable class of room within a property. base_price_per_night is the input
-- to a PricingStrategy, never the price actually charged: that lives per-night on
-- daily_inventory, written at materialisation time (design doc 4.2.1).
-- =====================================================================================
CREATE TABLE room_types (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_uid         VARCHAR(36)   NOT NULL,
    property_id           BIGINT        NOT NULL,
    name                  VARCHAR(160)  NOT NULL,
    total_units           INT           NOT NULL,
    max_guests            INT           NOT NULL,
    base_price_per_night  DECIMAL(12,2) NOT NULL,
    CONSTRAINT uq_room_type_uid UNIQUE (room_type_uid),
    CONSTRAINT fk_room_type_property FOREIGN KEY (property_id) REFERENCES properties (id)
) ENGINE = InnoDB;

-- =====================================================================================
-- daily_inventory — one room-night. The correctness backstop of the whole reservation design
-- (design doc 5.2.4 layer 3): the CHECK constraints below make overbooking impossible even
-- from a hand-written query, independent of anything the application layer gets right.
-- Deliberately no room_type_id -> room_types FK and no @Version column - see that entity's
-- own Javadoc: reservation is a single atomic conditional UPDATE with no read-then-write
-- window, so there is nothing for optimistic locking to protect here.
-- =====================================================================================
CREATE TABLE daily_inventory (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id   BIGINT        NOT NULL,
    stay_date      DATE          NOT NULL,
    total_units    INT           NOT NULL,
    booked_units   INT           NOT NULL DEFAULT 0,
    price_per_unit DECIMAL(12,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    -- One row per (room type, night); this is also what the reservation UPDATE's WHERE
    -- clause and search's availability batch fetch both key off.
    CONSTRAINT uq_inventory_slot UNIQUE (room_type_id, stay_date),
    CONSTRAINT ck_not_overbooked CHECK (booked_units <= total_units),
    CONSTRAINT ck_non_negative   CHECK (booked_units >= 0),
    CONSTRAINT ck_price_positive CHECK (price_per_unit > 0),
    -- Covers both the single-night reservation UPDATE and the ranged lookups
    -- (DailyInventoryRepository.findByRoomTypeIdAndStayDateBetween /
    -- findByRoomTypeIdInAndStayDateBetween) that search and admin views both use.
    INDEX idx_inventory_lookup (room_type_id, stay_date)
) ENGINE = InnoDB;

-- =====================================================================================
-- guests — the guest profile (design doc 12.6.1). Referenced from bookings by guest_id only;
-- personal data never inlines into an append-only record (12.6.3). redacted_at is the
-- erasure tombstone: non-null means this guest has been erased.
--
-- Every PII column is ENCRYPTED AT REST (Phase 10): AES-GCM via EncryptedStringConverter /
-- EncryptedLocalDateConverter, stored as "v1:" + Base64(iv || ciphertext || tag). Three
-- consequences are visible in the DDL below and are not accidental:
--   1. The columns are wide. An encrypted value is roughly 4*ceil((28 + utf8Bytes)/3) + 3
--      characters, so each is sized for its GuestDetails @Size limit at the UTF-8 worst case
--      of four bytes per character (address is capped at 300 characters on input and needs
--      ~1640). The DTO's validation is what keeps these bounds honest.
--   2. date_of_birth is VARCHAR, not DATE. It is stored ISO-8601 then encrypted, so it can no
--      longer be range-queried or compared in SQL — a capability nothing in this system used
--      for this column, which is exactly why encrypting it is defensible here and would not be
--      on bookings.check_in.
--   3. guest_uid is deliberately NOT encrypted: it is the opaque handle every lookup and every
--      append-only table uses. Encrypting it would break findByGuestUid outright.
-- redacted_at also stays plaintext, so "this guest was erased" remains auditable without
-- decrypting anything. Encryption at rest does not replace erasure (12.6.3) — the tombstone is
-- still really written over the plaintext; it protects against a reader of the storage layer,
-- which erasure never addressed.
-- =====================================================================================
CREATE TABLE guests (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    guest_uid      VARCHAR(36)  NOT NULL,
    full_name      VARCHAR(1024),
    email          VARCHAR(1536),
    phone          VARCHAR(256),
    address        VARCHAR(2048),
    date_of_birth  VARCHAR(128),
    redacted_at    DATETIME(6),
    -- Deliberately no unique index on email: two bookings by the same person are not a
    -- conflict, and a unique index on a redactable column would let an erasure tombstone
    -- collide with another still-live row sharing the placeholder value. Encryption makes the
    -- point moot besides — a random IV per value means the same email encrypts differently
    -- every time, so a unique index on it could not detect duplicates even if one existed.
    CONSTRAINT uq_guest_uid UNIQUE (guest_uid)
) ENGINE = InnoDB;

-- =====================================================================================
-- bookings — a reservation of `units` rooms of one room type across one date range. Carries
-- no guest name/email/phone/address (guest_id is a reference only) so PII can be erased
-- without touching booking or financial history. The @Version column is the mechanism the
-- sweeper-versus-payment race relies on (design doc 4.4, Phase 9's SweeperPaymentRaceTest).
-- =====================================================================================
CREATE TABLE bookings (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_uid      VARCHAR(36)   NOT NULL,
    guest_id         BIGINT        NOT NULL,
    property_id      BIGINT        NOT NULL,
    room_type_id     BIGINT        NOT NULL,
    check_in         DATE          NOT NULL,
    check_out        DATE          NOT NULL,
    units            INT           NOT NULL,
    adults           INT           NOT NULL,
    children         INT           NOT NULL,
    total_amount     DECIMAL(12,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    hold_expires_at  DATETIME(6)   NOT NULL,
    state            VARCHAR(30)   NOT NULL,
    version          BIGINT,
    CONSTRAINT uq_booking_uid UNIQUE (booking_uid)
    -- No FK to guests/properties/room_types: each is a plain business-id-bearing column by
    -- design (this table is not permitted to leak PII, and the reservation hot path never
    -- needs to join through a lazy proxy to reach it).
) ENGINE = InnoDB
-- Partition-by-range on check_in year (design doc 16): partition pruning on date-bounded
-- reports (a "bookings for 2027" query touches one partition, not the whole table), and a
-- cheap DROP PARTITION for retention instead of a row-by-row DELETE. Commented out rather
-- than live for two reasons stated in design doc 16 itself: H2 (this project's actual,
-- always-on database) has no partitioning support at all, and at this project's seed-data
-- volume — tens of bookings, not tens of millions — partitioning would demonstrate nothing
-- measurable even against real MySQL. It is documented here as the production evolution
-- path, not exercised.
-- PARTITION BY RANGE (YEAR(check_in)) (
--     PARTITION p2026 VALUES LESS THAN (2027),
--     PARTITION p2027 VALUES LESS THAN (2028),
--     PARTITION p2028 VALUES LESS THAN (2029),
--     PARTITION pmax  VALUES LESS THAN MAXVALUE
-- )
;

-- =====================================================================================
-- booking_line_items — the price for one night of one booking, snapshotted at booking time
-- and never recomputed (design doc 4.2.1/5.2's price-snapshot rule): a later rate change on
-- daily_inventory must not alter what an existing booking owes.
-- =====================================================================================
CREATE TABLE booking_line_items (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id     BIGINT        NOT NULL,
    stay_date      DATE          NOT NULL,
    units          INT           NOT NULL,
    price_per_unit DECIMAL(12,2) NOT NULL,
    line_total     DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_line_item_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE = InnoDB;

-- =====================================================================================
-- payments — one payment attempt against a booking. provider_reference is generated once and
-- reused on every retry (idempotency layer (b), design doc 8b): a fresh reference on retry is
-- the standard double-charge bug.
-- =====================================================================================
CREATE TABLE payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_uid         VARCHAR(36)   NOT NULL,
    booking_id          BIGINT        NOT NULL,
    method              VARCHAR(20)   NOT NULL,
    bank_code           VARCHAR(40),
    provider_code       VARCHAR(40),
    provider_reference  VARCHAR(64)   NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    state               VARCHAR(20)   NOT NULL,
    unknown_since       DATETIME(6),
    attempt_no          INT           NOT NULL DEFAULT 0,
    next_attempt_at     DATETIME(6),
    inventory_released  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT,
    CONSTRAINT uq_payment_provider_reference UNIQUE (provider_reference),
    -- What PaymentReconciliationService.run() scans on every pass: "every UNKNOWN payment
    -- whose next scheduled check is due". Without this, that scan is a full table scan of
    -- every payment ever created.
    INDEX idx_payment_due (state, next_attempt_at)
) ENGINE = InnoDB;

-- =====================================================================================
-- payment_status_checks — one row per reconciliation attempt against a stuck payment (design
-- doc 7.6.5). Append-only: the full poll history for a disputed transaction is
-- reconstructible from this table alone, which is what an escalation to a payment partner
-- actually requires. response_summary is already redacted before this row is ever built
-- (design doc 12.6.6) - never the gateway's raw response.
-- =====================================================================================
CREATE TABLE payment_status_checks (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        BIGINT      NOT NULL,
    attempt_no        INT         NOT NULL,
    checked_at        DATETIME(6) NOT NULL,
    next_attempt_at   DATETIME(6),
    gateway_status    VARCHAR(20) NOT NULL,
    response_summary  VARCHAR(500),
    correlation_id    VARCHAR(64),
    INDEX idx_status_check_due (gateway_status, checked_at)
) ENGINE = InnoDB;

-- =====================================================================================
-- idempotency_records — idempotency layer (a) of design doc 8: client -> API, keyed on the
-- client-supplied msgId. msg_id is the primary key itself, not a generated one: the
-- UNIQUE-by-being-a-key constraint is what serialises two genuinely concurrent requests
-- carrying the same id - the database constraint does the work, not application locking.
-- Evicted by IdempotencyRecordSweeper once older than payment.idempotency.retention (Phase 9)
-- - unbounded growth on a dedupe table is a real production problem (design doc 8a).
-- =====================================================================================
CREATE TABLE idempotency_records (
    msg_id          VARCHAR(100) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    response_body   LONGTEXT,
    created_at      DATETIME(6)  NOT NULL,
    api_type        VARCHAR(40),
    correlation_id  VARCHAR(64),
    -- IdempotencyRecordSweeper's bulk eviction deletes by this column; without an index that
    -- delete is a full table scan every time the sweeper runs.
    INDEX idx_idempotency_created_at (created_at)
) ENGINE = InnoDB;

-- =====================================================================================
-- ledger_entries — one immutable line in the financial trail (design doc 9.4). Never
-- updated, never deleted; a correction is a new row, never a change to an old one. Balance is
-- derived by summing these, not stored, which is what lets "why is this number what it is"
-- have an answer. No FK to bookings/payments by design (plain reference columns, matching
-- every other append-only audit table in this schema).
-- =====================================================================================
CREATE TABLE ledger_entries (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_entry_uid    VARCHAR(36)   NOT NULL,
    booking_id          BIGINT        NOT NULL,
    payment_id          BIGINT,
    type                VARCHAR(20)   NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    direction           VARCHAR(10)   NOT NULL,
    provider_reference  VARCHAR(64),
    occurred_at         DATETIME(6)   NOT NULL,
    correlation_id      VARCHAR(64),
    CONSTRAINT uq_ledger_entry_uid UNIQUE (ledger_entry_uid),
    -- The invariant check (sum(REFUND)+sum(REVERSAL) <= sum(CHARGE) per booking) and the
    -- admin ledger view both query "every entry for this booking, in order" - this index is
    -- the difference between that being indexed and being a table scan.
    INDEX idx_ledger_booking (booking_id, occurred_at)
) ENGINE = InnoDB;

-- =====================================================================================
-- refunds — a guest-initiated refund against a settled payment (design doc 9.1, 9.5): "money
-- back, per policy", triggered by cancelling a confirmed booking. Distinct from reversals,
-- which are always full and never policy-applied.
-- =====================================================================================
CREATE TABLE refunds (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_uid          VARCHAR(36)   NOT NULL,
    booking_id          BIGINT        NOT NULL,
    payment_id          BIGINT        NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    policy_code         VARCHAR(40),
    state               VARCHAR(20)   NOT NULL,
    provider_reference  VARCHAR(64),
    requested_at        DATETIME(6)   NOT NULL,
    completed_at        DATETIME(6),
    version             BIGINT,
    CONSTRAINT uq_refund_uid UNIQUE (refund_uid)
) ENGINE = InnoDB;

-- =====================================================================================
-- reversals — undoes a settled payment because the transaction should not have stood, never
-- because the guest asked for money back (design doc 9.1). Always full, never
-- policy-applied - what distinguishes a reversal from a refund.
-- =====================================================================================
CREATE TABLE reversals (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    reversal_uid        VARCHAR(36)   NOT NULL,
    booking_id          BIGINT        NOT NULL,
    payment_id          BIGINT        NOT NULL,
    reason              VARCHAR(40)   NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    state               VARCHAR(20)   NOT NULL,
    provider_reference  VARCHAR(64),
    occurred_at         DATETIME(6)   NOT NULL,
    version             BIGINT,
    CONSTRAINT uq_reversal_uid UNIQUE (reversal_uid)
) ENGINE = InnoDB;

-- =====================================================================================
-- webhook_event_log — every inbound callback, persisted before it is processed (design doc
-- 12.3 step 6, 9.6). Holds no personal data - only the already-redacted payload and opaque
-- identifiers (12.6.3). The UNIQUE (provider_code, event_id) constraint is idempotency layer
-- (c) of design doc 8c: the constraint itself, not an exists-check alone, is what serialises
-- two genuinely concurrent deliveries of the same callback.
-- =====================================================================================
CREATE TABLE webhook_event_log (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_event_log_uid    VARCHAR(36)  NOT NULL,
    provider_code            VARCHAR(40)  NOT NULL,
    event_id                 VARCHAR(100) NOT NULL,
    event_type               VARCHAR(40),
    signature_valid          BOOLEAN      NOT NULL,
    outcome                  VARCHAR(30)  NOT NULL,
    payload                  LONGTEXT,
    correlation_id           VARCHAR(64),
    received_at              DATETIME(6)  NOT NULL,
    processed_at             DATETIME(6),
    CONSTRAINT uq_webhook_event_log_uid UNIQUE (webhook_event_log_uid),
    CONSTRAINT uq_webhook_event UNIQUE (provider_code, event_id)
) ENGINE = InnoDB;
