# Hotel Booking Platform

Full README content — build/run instructions, sample cURL flows, architecture summary and the
design-decision writeups — is Phase 9 (see `PROJECT_STRUCTURE.txt.txt` §17, §18). This file
carries only the one note Phase 6 was told to record now so it is not forgotten:

- **Role separation is structural.** `ADMIN`, `USER` and `SYSTEM` are distinct URL spaces
  (`/api/v1/admin/**`, `/api/v1/user/**`, `/api/v1/webhooks/**`), distinct controller packages
  (`controller.admin`, `controller.user`, `controller.webhook`), and a distinct `@RequireRole`
  annotation per controller. Enforcement is stubbed — a mismatched `X-Role` header is rejected,
  a missing one is let through — since authorisation itself is out of scope for this exercise
  (design doc 11.4).

- **Search is advisory, not a reservation.** `POST /api/v1/user/properties/search` reads
  `DailyInventory` without taking any lock, so the availability and price it returns can be
  stale by the time a client calls booking — another guest can book the same room-night in
  between. This raciness is inherent to any search-then-book design and is not treated as a bug
  to fix here: the booking path is the single source of truth, re-validating and reserving
  atomically under the conditional-UPDATE scheme of design doc 5.2.1 regardless of what search
  said. A booking that loses the race does not fail vaguely — it fails with the same
  `InventoryUnavailableException` the booking path always throws, naming the exact room type
  and night ("No availability for room type {roomTypeUid} on {date}"), so a client that acted on
  a stale search result gets a specific, actionable rejection rather than a generic error. A
  soft-hold on search (reserving units for a short TTL before checkout, the way some booking
  sites do) was considered and rejected for this exercise: it adds a second expiring-reservation
  mechanism alongside the booking hold TTL of design doc 4.4, complicates the sweeper, and holds
  real inventory hostage to abandoned searches — a cost not justified by the exercise's scope.
  Search also has no cursor pagination; results are capped at `search.max-results` (default 50)
  and the response reports `truncated: true` when the cap bites, rather than expose a paging API
  that nothing in this exercise's scope needs.
