# API walkthrough and OpenAPI

Full cURL walkthrough of every core flow, plus what had to be customised to make the
generated OpenAPI document accurate. Back to the [README](../README.md).

## API walkthrough (cURL)

Every state-changing request carries the request envelope
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
acts on it after someone else took the room (see [Assumptions](../README.md#assumptions)).

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
