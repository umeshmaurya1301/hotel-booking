# Production evolution, MySQL profile, and Docker

What is deliberately not built and where it would go, plus the two optional run paths.
Back to the [README](../README.md).

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
> | Docker | Not required by the brief. Added last, only once tests and README were complete. |

## MySQL reference profile

`application-mysql.yml` and `schema-mysql.sql` (`src/main/resources/`) are committed and real,
closing a gap design doc 13.1 had asserted was already closed when it was not. Not active by
default — nothing loads them unless a run
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
`./gradlew test` (see [What would come next](../README.md#what-would-come-next-with-more-time)).

## Docker

A `Dockerfile` is included and is explicitly **not** the primary way to run this project —
design doc 16 lists Docker as "not required by the brief, added last, only once tests and README are
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
