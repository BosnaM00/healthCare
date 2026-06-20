# MediConnect — Stripe Connect Implementation Prompt for Claude Opus 4.7

You are implementing the **Stripe Connect payments and payouts subsystem** for MediConnect, a Romanian telemedicine marketplace. Two repositories are involved: a Java Spring Boot backend (`healthCare`) and a React/Vite/TypeScript frontend (`my-app`). This prompt is self-contained — read it fully, study the referenced documents, then proceed.

---

## 1. Project context

MediConnect is a hybrid marketplace connecting patients with both clinic-affiliated and independent medics. The platform must comply with GDPR (Art. 9 special-category health data), PSD2/SCA, and Romanian VAT/payout requirements. All amounts are in Romanian leu (`RON`); Stripe expects minor units (`bani`, 1 RON = 100 bani).

The platform uses an **escrow-style charge model**: when a patient books a consultation, funds are captured immediately on the patient's card and held by the platform Stripe account until the consultation completes successfully. Funds are then transferred to the medic's Stripe Connect (Express) account, minus the platform fee. If the consultation fails (no-show, technical failure, dispute), the funds are refunded according to a documented matrix of failure scenarios.

**Out of scope for this work:** Keycloak migration. The project currently uses a custom Spring Security + JWT implementation; do NOT add or modify any Keycloak code, configuration, or dependencies.

---

## 2. Reference documents (READ FIRST)

You must read all four documents before writing any code:

1. `mnt/healthCare/stripe-plan.docx` — the canonical implementation plan for this work. Every section, table, and code snippet in that document is binding unless this prompt explicitly overrides it.
2. `mnt/uploads/mediconnect_architecture.docx` — the backend architecture spec (roles, services, payment escrow flow, failed-consultation matrix, audit requirements).
3. `mnt/uploads/MediConnect_UI_Architecture.docx` — the UI architecture spec (design system, component catalogue, accessibility, i18n, state management).
4. The current code on the two source branches (see §3).

If any conflict exists between these documents, the order of precedence is: this prompt → `stripe-plan.docx` → `mediconnect_architecture.docx` / `MediConnect_UI_Architecture.docx` → existing code.

---

## 3. Branch setup (do this first)

Create two new feature branches and do all work on them. Do not touch `main` or any other branch.

**Backend (`healthCare` repository):**

```bash
cd healthCare
git fetch --all --prune
git checkout feature/improvements-1
git pull --ff-only
git checkout -b feature/improvements-2-stripe
```

**Frontend (`my-app` repository):**

```bash
cd my-app
git fetch --all --prune
git checkout feature/improvements-3
git pull --ff-only
git checkout -b feature/improvements-4-stripe
```

Push each branch upstream on first commit so the remote tracking is set:

```bash
git push -u origin feature/improvements-2-stripe   # backend
git push -u origin feature/improvements-4-stripe   # frontend
```

---

## 4. Binding architecture decisions

These decisions are already made — do not relitigate them in code. They are also recorded in §1 of the plan document.

| # | Decision | Value |
|---|----------|-------|
| 1 | Stripe account model | **Connect Express** for medics |
| 2 | Charge model | PaymentIntent on platform account → Transfer to medic on release |
| 3 | Currency | `RON` only (minor unit `bani`) |
| 4 | Capture mode | `automatic` (charge-now-transfer-later) |
| 5 | 3DS / SCA | `automatic_payment_methods.enabled = true`, allow redirects |
| 6 | Webhook signature tolerance | 300 seconds (Stripe default) |
| 7 | Idempotency keys | `pi:<bookingId>` for create, `<paymentId>:release` for transfer, `<paymentId>:refund:<reason>` for refund, `<paymentId>:reverse` for transfer reversal |
| 8 | Dispute window | 120 minutes after consultation end before auto-release |
| 9 | Cancellation window | 2 hours before consultation start = full refund; later = forfeit per matrix |
| 10 | Invoicing (Stripe Invoices/Tax) | Deferred to Phase 2 — not part of this work |
| 11 | Required PaymentIntent metadata | `bookingId`, `patientId`, `medicId`, `serviceId`, `consultationStartUtc` |

---

## 5. Backend implementation (`healthCare`, branch `feature/improvements-2-stripe`)

Follow §2 of the plan document section by section. Below is the minimum acceptance bar — the plan document contains exact SDK signatures and config snippets.

### 5.1 Dependencies & configuration
- `stripe-java` is already at `26.3.0`; do not downgrade.
- Externalise `stripe.api.key`, `stripe.webhook.secret`, `stripe.connect.return-url`, `stripe.connect.refresh-url`, `app.fee.percent`, `app.fee.fixed-bani` to `application.yml` with `${ENV_VAR}` placeholders. Document required env vars in `README.md`.
- Initialise `Stripe.apiKey` once at startup via a `StripeConfig` `@Configuration` bean that reads from properties (no static init in services).

### 5.2 Switch Quartz from RAMJobStore to JDBCJobStore
- Add Quartz JDBC schema migration (PostgreSQL flavour) under `src/main/resources/db/migration/` (Flyway). If Flyway is not yet enabled, enable it as part of this work — see §5.4.
- Configure `org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX`, `driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate`, `isClustered=true`.
- Move the auto-release job (`AutoReleaseConsultationJob`) onto Quartz with a misfire policy `MISFIRE_INSTRUCTION_FIRE_NOW`. Schedule it 120 minutes after `consultationEndUtc`.

### 5.3 Domain & schema
Add or extend these JPA entities (all mutations via Flyway migration):
- `Payment` — fields: `id`, `bookingId`, `patientId`, `medicId`, `amountBani` (long), `applicationFeeBani`, `currency` ('RON'), `state` (`RESERVED`, `HELD`, `RELEASED`, `REFUNDED`, `DISPUTED`, `FAILED`), `stripePaymentIntentId`, `stripeChargeId`, `stripeTransferId`, `stripeRefundId`, `stripeDisputeId`, `clientSecret` (transient), `createdAt`, `updatedAt`, `version` (optimistic lock).
- `MedicStripeAccount` — `medicId` (PK, FK), `stripeAccountId`, `chargesEnabled`, `payoutsEnabled`, `detailsSubmitted`, `requirementsCurrentlyDueJson`, `lastSyncedAt`.
- `WebhookEvent` — `id` (Stripe event id, PK), `type`, `payload` (TEXT), `receivedAt`, `processedAt`, `status` (`PENDING`, `PROCESSED`, `FAILED`), `attempts`. Used for idempotency and replay protection.
- Indices: `payment(stripePaymentIntentId)`, `payment(bookingId)`, `payment(state, updatedAt)`, `medic_stripe_account(stripeAccountId)`.

Use `BigDecimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()` to convert RON to bani everywhere; never use `double` for money.

### 5.4 Flyway
- Enable `spring-boot-starter-flyway` if absent. Set `spring.flyway.enabled=true`, `baseline-on-migrate=true`.
- All schema changes (Stripe tables + Quartz tables) must ship as numbered migrations (`V1__`, `V2__`, …) under `src/main/resources/db/migration/`.

### 5.5 Service layer
- `StripeConnectService` — `createOnboardingLink(medicId)`, `refreshAccountStatus(medicId)`, `loginLink(medicId)`. Use `AccountCreateParams` with `type=EXPRESS`, `country='RO'`, `default_currency='ron'`, capabilities `card_payments` + `transfers`. Onboarding via `AccountLinkCreateParams` with `type=ACCOUNT_ONBOARDING` and configured return/refresh URLs.
- `PaymentService.reserve(bookingId)` — creates `Payment(state=RESERVED)`, calls `PaymentIntent.create` with `automatic_payment_methods.enabled=true`, `transfer_group=booking_<id>`, the metadata in §4 row 11, and `RequestOptions.builder().setIdempotencyKey("pi:" + bookingId).build()`. Returns `clientSecret` to caller. Move state to `HELD` on `payment_intent.succeeded` webhook.
- `PaymentService.release(paymentId)` — guarded by Quartz job; creates `Transfer.create` with `destination=medic.stripeAccountId`, `source_transaction=charge id`, `amount=amountBani - applicationFeeBani`, `transfer_group=booking_<id>`, idempotency key `<paymentId>:release`. Transition `HELD → RELEASED`.
- `PaymentService.refund(paymentId, reason)` — `Refund.create` with `payment_intent`, `reason`, idempotency `<paymentId>:refund:<reason>`. If transfer already happened, also `Transfer.createReversal` with idempotency `<paymentId>:reverse`. Transition to `REFUNDED`.
- `PaymentService.handleDispute(eventPayload)` — transition to `DISPUTED`, suspend any pending release, log audit event, notify operations.
- All state transitions use a single `transitionState(payment, expectedFrom, to)` method that throws if `payment.state != expectedFrom` (concurrency guard via JPA `@Version`).

### 5.6 Webhook endpoint
- Path: `POST /api/v1/webhooks/stripe`. **Public** (no auth filter), but must reject any request whose `Stripe-Signature` does not verify with the configured `webhook.secret` (use `Webhook.constructEvent`).
- Read raw body via `HttpServletRequest.getInputStream()` — do NOT let Spring deserialise it; signature verification must run on the exact raw bytes.
- Persist the event to `WebhookEvent` before handling. If a row already exists for the event id, return `200` immediately (idempotent replay).
- Dispatch table — implement handlers for at minimum:

| Event type | Action |
|------------|--------|
| `payment_intent.succeeded` | `RESERVED → HELD`, persist `chargeId` |
| `payment_intent.payment_failed` | `RESERVED → FAILED`, free booking slot |
| `payment_intent.canceled` | `RESERVED → FAILED` |
| `charge.refunded` | record `refundId`, transition to `REFUNDED` if full refund |
| `charge.dispute.created` | `→ DISPUTED`, halt release |
| `charge.dispute.closed` | reconcile per `outcome` |
| `transfer.created` | record `transferId` on the matching payment |
| `transfer.reversed` | reconcile reversal |
| `account.updated` (Connect) | re-sync `MedicStripeAccount` flags |
| `account.application.deauthorized` | mark medic as offboarded, block new bookings |
| `payout.paid` / `payout.failed` | log audit event for medic-facing payout history |

- Return `200` after successful handler completion; on handler exception, return `500` so Stripe retries (do NOT swallow).

### 5.7 REST API surface
All endpoints under `/api/v1`, secured with the existing JWT filter, role-checked via `@PreAuthorize`:

- `POST /payments/intents` — body `{ bookingId }`, role `PATIENT`, returns `{ paymentId, clientSecret, publishableKey }`.
- `GET /payments/{id}` — owner or operator only.
- `POST /payments/{id}/refund` — body `{ reason }`, role `OPERATOR` or `PATIENT` (within cancellation window).
- `POST /medics/me/stripe/onboarding` — role `MEDIC`, returns `{ url }`.
- `GET /medics/me/stripe/status` — role `MEDIC`, returns capability flags.
- `GET /medics/me/payouts` — role `MEDIC`, paginated payout history (proxied from `Payout.list`).
- `GET /webhooks/stripe/health` — operator-only diagnostic endpoint showing last event timestamps per type.

Use RFC 7807 `application/problem+json` for all errors with a `traceId` field. Do not leak Stripe error messages directly to patients — translate via `StripeErrorMapper`.

### 5.8 Audit & notifications
- Auto-log every payment state transition to the existing audit table (or create one if missing) with `actor`, `action`, `paymentId`, `fromState`, `toState`, `correlationId`, `timestamp`.
- Fire domain events (`PaymentHeldEvent`, `PaymentReleasedEvent`, `PaymentRefundedEvent`, `PaymentDisputedEvent`) so the future notification service can subscribe. A no-op `NotificationPublisher` is acceptable for now.

### 5.9 Encryption & secrets
- Replace placeholder AES-256-GCM keys with values resolved from environment variables (`APP_ENCRYPTION_KEY_PRIMARY`, `APP_ENCRYPTION_KEY_SECONDARY` for rotation). Fail fast on startup if missing or wrong length (32 bytes Base64).
- Stripe API key, webhook secret, and DB password must be read from env / vault — never from `application.yml` defaults.

### 5.10 CORS & security
- Add `/api/v1/webhooks/**` to the public matcher list in `SecurityConfig`, but ensure it is NOT in the CORS allow-list (Stripe calls it server-to-server).
- Allow only the frontend origin(s) defined by `app.cors.allowed-origins` (comma-separated env var).

### 5.11 Testing
- Unit tests for `PaymentService` state machine using JUnit 5 + Mockito.
- Integration tests for the webhook endpoint using Testcontainers (PostgreSQL) + the Stripe `MockWebServer` pattern: feed real Stripe-formatted JSON payloads with signatures generated by the test using the configured webhook secret. Cover all 11 events listed in §5.6.
- Idempotency test: send the same webhook twice → second call returns 200 without re-processing.
- Concurrency test: two threads attempt `release` on the same payment → exactly one succeeds, the other gets an optimistic-lock failure surfaced as 409.

---

## 6. Frontend implementation (`my-app`, branch `feature/improvements-4-stripe`)

Follow §3 of the plan document. The frontend uses React 19 + Vite + TypeScript (strict), Tailwind v4, shadcn/ui, TanStack Query, Zustand, react-hook-form + Zod, CASL, MSW, i18next. TanStack Router is installed but currently unused — do not migrate to it as part of this work.

### 6.1 Dependencies
Install:
- `@stripe/stripe-js` (latest stable major)
- `@stripe/react-stripe-js` (matching major)

Pin major versions in `package.json`. Do not introduce other Stripe-adjacent libraries.

### 6.2 Environment & config
- Add `VITE_STRIPE_PUBLISHABLE_KEY` to `.env.example`. Read it via a typed `env.ts` helper that throws at startup if missing.
- The publishable key may also be returned by the backend on `POST /payments/intents`; prefer the backend value when present (lets us rotate keys without redeploying the SPA).

### 6.3 Stripe Elements wrapper
Create `src/features/payments/StripeProvider.tsx`:
- Wraps `Elements` from `@stripe/react-stripe-js`.
- Calls `loadStripe` once, memoised via module-level promise.
- Accepts `clientSecret` and `appearance` (use brand tokens — deep teal `#0F766E`, error red, system font stack).
- Locale: read from `i18next` (default `ro`, fallback `en`).

### 6.4 BookingSheet payment step
- Extend the existing booking flow so that after slot/service selection the patient sees a payment step rendered by `<PaymentElement />` inside `<StripeProvider>`.
- Fetch the `clientSecret` via TanStack Query mutation calling `POST /payments/intents`. Show optimistic UI; on error, surface the RFC 7807 `detail` translated via `StripeErrorMapper.t(code)`.
- On `stripe.confirmPayment`, redirect URL = `${origin}/bookings/{bookingId}/result`. Handle `requires_action` (3DS) automatically — Stripe Elements does this when `automatic_payment_methods` is enabled.
- Disable submit while `stripe`/`elements` are not ready or while `useElements().getElement(PaymentElement)?.complete !== true`.

### 6.5 Booking result page
Route `/bookings/:id/result` (hand-rolled router — match the existing pattern, do NOT introduce TanStack Router):
- Reads `payment_intent` and `payment_intent_client_secret` from the query string.
- Calls `stripe.retrievePaymentIntent(clientSecret)` and shows a status card matching the table below.
- Polls `GET /payments/{id}` every 3s for up to 30s if backend state is still `RESERVED` (gives the webhook time to land).

### 6.6 StripeStatusBadge
Single component `<StripeStatusBadge state="HELD" />` used everywhere. Status → colour → location matrix:

| State | Colour token | Patient bookings | Medic dashboard | Operator console |
|-------|--------------|------------------|-----------------|------------------|
| `RESERVED` | amber | "Awaiting confirmation" | "Pending" | shown |
| `HELD` | teal | "Paid — held in escrow" | "Earnings pending" | shown |
| `RELEASED` | green | "Completed" | "Paid out" | shown |
| `REFUNDED` | slate | "Refunded" | "Refunded" | shown |
| `DISPUTED` | red | hidden | "Under review" | shown |
| `FAILED` | red | "Payment failed" | hidden | shown |

All strings are i18n keys (`payments.status.*`) with `ro` and `en` translations.

### 6.7 Medic payouts pages
- `/medic/onboarding` — calls `POST /medics/me/stripe/onboarding`, redirects to the returned `url`. On return, polls `GET /medics/me/stripe/status` and shows checklist (`chargesEnabled`, `payoutsEnabled`, `detailsSubmitted`).
- `/medic/payouts` — paginated table of payouts from `GET /medics/me/payouts` with status, amount (formatted RON), arrival date, and a "View on Stripe" link if `loginLink` is provided by the backend.
- Gate access via CASL ability `can('manage', 'OwnPayouts')`.

### 6.8 i18n
- Initialise `i18next` if not already wired (`src/i18n/index.ts`). Languages `ro` (default) and `en`.
- Add namespaces `payments`, `payouts`, `stripe`. All user-visible Stripe-related strings go through `t('…')`; no hard-coded literals.

### 6.9 Testing
- Unit tests for `StripeStatusBadge`, `StripeErrorMapper`, currency formatter (use `Intl.NumberFormat('ro-RO', { style:'currency', currency:'RON' })`).
- MSW handlers for `/api/v1/payments/*` and `/api/v1/medics/me/stripe/*`. Add Storybook stories for the payment step happy path, failure path, and 3DS-redirect path. (Storybook may not yet be installed — only add it if the team wants it; otherwise document it as a follow-up.)
- Vitest + React Testing Library coverage for the booking-result polling logic, including the "webhook arrives during polling" case.

---

## 7. Cross-cutting work (both repos)

| Concern | Backend action | Frontend action |
|---------|---------------|-----------------|
| Secrets | All Stripe keys via env vars; document in README | Same; Vite env files |
| CORS | Allow only configured origins; webhook endpoint excluded | n/a |
| Audit | Every state transition logged with correlationId | n/a |
| i18n | Error messages keyed (RO/EN) | All UI copy keyed |
| Feature flag | `app.payments.stripe-enabled` boolean — gate all new routes; default `true` in dev, `false` in prod until QA passes | `VITE_FEATURE_STRIPE` boolean — hide payment step + payouts pages when off |
| Logging | Redact PAN-like patterns in case any leak through `metadata`; never log full event payload, only `id` + `type` | Console-error mapping does not echo raw Stripe error |

---

## 8. Phased delivery (commits & PRs)

Land work in this order. Each phase = at least one commit with a descriptive message; phases A–D should each be a separate PR (draft is fine). E–G can land together.

- **Phase A — Foundations.** Properties, Flyway, `StripeConfig`, Quartz JDBC migration, schema migrations for `Payment`, `MedicStripeAccount`, `WebhookEvent`. No Stripe SDK calls yet. Unit tests for `RonAmount` value object.
- **Phase B — Connect onboarding.** `StripeConnectService`, medic onboarding endpoints, frontend `/medic/onboarding` page. Manual test against Stripe test mode.
- **Phase C — Patient payment happy path.** `PaymentService.reserve`, `POST /payments/intents`, frontend `BookingSheet` payment step, webhook handler for `payment_intent.succeeded` and `.payment_failed`. State machine `RESERVED → HELD/FAILED`. Integration tests.
- **Phase D — Release & refund.** Quartz auto-release job, `release`/`refund` services & endpoints, transfer + transfer-reversal logic, dispute handling, all remaining webhooks.
- **Phase E — Operator visibility.** `/operator/payments` listing (already mocked? extend), webhook health endpoint, audit event surfacing.
- **Phase F — i18n & polish.** Translation pass, error mapping completeness, feature flag wiring, README updates.
- **Phase G — Phase 2 prep (placeholders only).** Stub interfaces for Stripe Invoices/Tax. No implementation — just leave `// TODO: Stripe Invoices in Phase 2` markers and document in README.

### Commit messages

Use conventional commits scoped to either `backend` or `frontend`, e.g.:
- `feat(backend): add PaymentIntent reservation flow with idempotency`
- `feat(frontend): wire PaymentElement into BookingSheet`
- `fix(backend): verify webhook signature on raw body`
- `chore(backend): switch Quartz to JDBC JobStore`

### Pull requests

Open two PRs (one per repo). Title each `Stripe Connect — <repo> implementation` and link the plan document in the description. Include a checklist of phases A–G in the PR body. Do not merge to `main`; leave for review.

---

## 9. Failure scenarios matrix (binding)

Implement these exactly as described in `mediconnect_architecture.docx`. Summary for quick reference:

| Scenario | Patient | Medic | Platform |
|----------|---------|-------|----------|
| Patient cancels ≥ 2h before start | Full refund | Nothing | No fee |
| Patient cancels < 2h | No refund | Full payout (less platform fee) | Fee charged |
| Patient no-show | No refund | Full payout (less platform fee) | Fee charged |
| Medic no-show | Full refund + apology credit | Nothing + strike | No fee |
| Technical failure attributable to platform | Full refund | Compensation per SLA | Platform absorbs |

Each scenario is exercised by an integration test.

---

## 10. Acceptance criteria

Done means **all** of the following:

1. Both branches build and pass their full test suites in CI.
2. `feature/improvements-2-stripe` (backend) compiles cleanly with `mvn -B verify` and exposes all endpoints in §5.7. Webhook handler verifies signatures on raw bodies and is idempotent on event id.
3. `feature/improvements-4-stripe` (frontend) builds with `npm run build` (no TS errors), passes `npm run lint`, passes `npm test`, and renders the booking flow end-to-end against a backend running in Stripe test mode.
4. Quartz uses JDBC JobStore in clustered mode; the auto-release job reschedules across restarts.
5. Flyway migrations are present, named `V<n>__<snake_case>.sql`, and apply cleanly to a fresh PostgreSQL instance.
6. No secrets are committed. All Stripe keys, webhook secret, and encryption keys come from environment variables; absence triggers a fail-fast startup error with a clear message.
7. All payment state transitions are persisted with `@Version` optimistic locking; concurrent release attempts result in exactly one success.
8. Patient-facing copy is in Romanian by default and falls back to English; switching language updates all payment status badges and error messages.
9. The five failure scenarios in §9 each have an integration test that asserts the correct money flow and ledger state.
10. CORS allows only configured origins; the Stripe webhook endpoint is excluded from CORS and from the JWT filter chain but still rejects requests with invalid signatures.
11. `README.md` in each repo lists every new env var, the local dev workflow (Stripe CLI for webhook forwarding, test card numbers `4242 4242 4242 4242`, `4000 0027 6000 3184` for 3DS, `4000 0000 0000 0002` for declines), and how to run tests.

---

## 11. Constraints & non-goals

- **Do not** add, modify, or reference Keycloak.
- **Do not** introduce new state-management libraries; use existing Zustand for any client state needed.
- **Do not** migrate to TanStack Router — keep the hand-rolled router pattern for now.
- **Do not** implement Stripe Invoices or Stripe Tax — explicitly Phase 2.
- **Do not** modify any code on `main`, `feature/improvements-1`, or `feature/improvements-3`. All work happens on the two new branches.
- **Do not** log full Stripe event payloads or PAN-like patterns. Use redacted, structured logs only.
- **Do not** swallow webhook handler exceptions. Let Stripe retry.
- **Do not** use `double` or `float` for money — `BigDecimal` and `long bani` only.

---

## 12. Working method

1. Read all four reference documents end to end.
2. Create the two branches as in §3.
3. Skim the existing `StripePaymentService`/`StripeConnectService` stubs in `healthCare` and the `BookingSheet`/`PaymentStep` placeholders in `my-app` so you know what already exists.
4. Implement Phase A on both repos, push, open draft PRs.
5. Implement Phases B–F on the same branches; push commits as you go. Keep PRs in draft until Phase F completes.
6. Run all tests locally before each push: backend `mvn -B verify`, frontend `npm run lint && npm run typecheck && npm test && npm run build`.
7. When Phases A–F are green, mark both PRs ready for review and post a summary comment listing each acceptance criterion in §10 with a checkmark and a link to the test that proves it.

If anything in this prompt or the reference documents is genuinely ambiguous (not just unfamiliar), pause and ask before guessing. Otherwise, proceed.
