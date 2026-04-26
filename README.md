# MediConnect — Backend (healthCare)

Spring Boot 4.0.5 · Java 21 · PostgreSQL · Stripe Connect · Quartz JDBC

---

## Required Environment Variables

All secrets are read from environment variables at startup.
The application will warn loudly (but not fail-fast) if Stripe keys are placeholders —
this allows local builds without real credentials.

| Variable | Required | Description |
|---|---|---|
| `STRIPE_API_KEY` | Yes (prod) | Stripe secret key. Prefix: `sk_test_` (test), `sk_live_` (prod) |
| `STRIPE_WEBHOOK_SECRET` | Yes (prod) | Webhook endpoint signing secret. Prefix: `whsec_` |
| `STRIPE_PUBLISHABLE_KEY` | Yes (prod) | Stripe publishable key returned to frontend. Prefix: `pk_test_`/`pk_live_` |
| `DB_URL` | No | PostgreSQL JDBC URL. Default: `jdbc:postgresql://localhost:5432/mediconnect` |
| `DB_USERNAME` | No | DB username. Default: `postgres` |
| `DB_PASSWORD` | No | DB password. Default: `postgres` |
| `APP_ENCRYPTION_KEY_PRIMARY` | No | AES-256 primary key. Base64-encoded 32 bytes. Generate: `openssl rand -base64 32` |
| `APP_ENCRYPTION_KEY_SECONDARY` | No | AES-256 rotation key (optional) |
| `APP_JWT_SECRET` | No | JWT HS256 secret. Base64-encoded 32 bytes. Generate: `openssl rand -base64 32` |
| `FE_BASE_URL` | No | Frontend base URL for Stripe Connect return/refresh URLs. Default: `http://localhost:5173` |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed origins. Default: `http://localhost:5173,http://localhost:3000` |
| `STRIPE_ENABLED` | No | Feature flag — disables all Stripe routes when `false`. Default: `true` |

---

## Local Development Setup

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for PostgreSQL)
- [Stripe CLI](https://stripe.com/docs/stripe-cli) (for webhook forwarding)

### 1. Start PostgreSQL

```bash
docker run -d \
  --name mediconnect-db \
  -e POSTGRES_DB=mediconnect \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

### 2. Set environment variables

```bash
export STRIPE_API_KEY=sk_test_YOUR_TEST_KEY
export STRIPE_WEBHOOK_SECRET=whsec_YOUR_WEBHOOK_SECRET  # set after step 5
export STRIPE_PUBLISHABLE_KEY=pk_test_YOUR_PUBLISHABLE_KEY
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup. The first run applies:
- `V1__stripe_schema_extensions.sql` — Stripe tables and columns
- `V2__quartz_jdbc_schema.sql` — Quartz JDBC JobStore tables

### 4. Forward Stripe webhooks (local testing)

```bash
stripe listen --forward-to http://localhost:8080/api/v1/webhooks/stripe
```

The CLI prints your webhook signing secret (`whsec_...`). Set it:

```bash
export STRIPE_WEBHOOK_SECRET=whsec_...
```

Restart the application after setting this.

### 5. Stripe test card numbers

| Card | Scenario |
|---|---|
| `4242 4242 4242 4242` | Successful payment |
| `4000 0027 6000 3184` | 3DS / SCA authentication required |
| `4000 0000 0000 0002` | Card declined |
| `4000 0000 0000 9995` | Insufficient funds |
| `4100 0000 0000 0019` | Fraudulent card |

Use any future expiry, any 3-digit CVC, any postal code.

---

## Running Tests

```bash
# All tests (unit + integration)
./mvnw -B verify

# Unit tests only
./mvnw -B test

# Integration tests only (requires Docker for Testcontainers)
./mvnw -B verify -Dtest.groups=integration
```

---

## API Overview

### Authentication

All endpoints (except public ones and the webhook) require a JWT bearer token:
```
Authorization: Bearer <token>
```

Obtain a token: `POST /api/v1/auth/login`

### Payment Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/payments/intents` | PATIENT | Create a PaymentIntent for a booking |
| `GET` | `/api/v1/payments/{id}` | Owner | Get payment details |
| `POST` | `/api/v1/payments/{id}/refund` | ADMIN, PATIENT | Refund a payment |
| `GET` | `/api/v1/payments/booking/{bookingId}` | Owner | Get payment by booking |
| `GET` | `/api/v1/payments/my/patient` | PATIENT | List own payments |
| `GET` | `/api/v1/payments/my/medic` | MEDIC | List own earnings |

### Medic Stripe Connect

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/medics/me/stripe/onboarding` | MEDIC | Start Express onboarding |
| `POST` | `/api/v1/medics/me/stripe/onboarding/refresh` | MEDIC | Refresh expired link |
| `GET` | `/api/v1/medics/me/stripe/status` | MEDIC | Capability flags |
| `GET` | `/api/v1/medics/me/stripe/login` | MEDIC | Express Dashboard link |

### Webhooks

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/webhooks/stripe` | Stripe signature | Stripe event receiver |
| `GET` | `/api/v1/webhooks/stripe/health` | ADMIN | Last event per type |

---

## Architecture Notes

### Payment State Machine

```
RESERVED ──► HELD ──► RELEASED   (happy path)
              │
              ├──► DISPUTED ──► RELEASED   (dispute → medic wins)
              │              └──► REFUNDED (dispute → patient wins)
              │
RESERVED ──► REFUNDED  (cancelled within window)
RESERVED ──► FAILED    (PI failed or cancelled)
```

### Failure Scenarios (§9 of spec)

| Scenario | Patient | Medic | Trigger |
|---|---|---|---|
| Cancel ≥ 2h before | Full refund | Nothing | `BookingService.cancel()` |
| Cancel < 2h before | No refund | Full payout | `BookingService.cancel()` |
| Patient no-show | No refund | Full payout | Auto via `ConsultationService` |
| Medic no-show | Full refund | Nothing + strike | Auto via `ConsultationService` |
| Technical failure | Full refund | Nothing | `ConsultationService.markFailed()` |

### Quartz JDBC JobStore

The `AutoReleaseConsultationJob` runs every 5 minutes. In clustered mode, the JDBC
JobStore ensures exactly one node executes each trigger.

Misfire policy: `MISFIRE_INSTRUCTION_FIRE_NOW` — a missed fire executes immediately after restart.

---

## Deferred Work (Phase 2)

- `StripeInvoiceService` — Romanian VAT invoicing via Stripe Tax
- Stripe saved payment methods (Customer + SetupIntent flow)
- Stripe Radar custom rules
- Medic payout history page (proxy `Payout.list`)
- On-demand / instant consultation Layer 2 payments

See `StripeInvoiceService.java` for TODO markers.
