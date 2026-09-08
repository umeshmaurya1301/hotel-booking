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
