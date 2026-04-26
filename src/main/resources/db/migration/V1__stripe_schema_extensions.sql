-- ============================================================
-- V1 — Stripe Connect schema extensions
-- ============================================================
-- Adds columns required by the Stripe escrow implementation
-- on top of the Hibernate-managed baseline (version 0).
-- All DDL is idempotent via IF NOT EXISTS / IF EXISTS guards.
-- ============================================================

-- ── payments table extensions ─────────────────────────────────────────────────

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS patient_id             UUID,
    ADD COLUMN IF NOT EXISTS medic_id               UUID,
    ADD COLUMN IF NOT EXISTS stripe_charge_id       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_refund_id       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_dispute_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS amount_bani            BIGINT        NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS application_fee_bani   BIGINT        NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency               VARCHAR(3)    NOT NULL DEFAULT 'RON',
    ADD COLUMN IF NOT EXISTS created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version                BIGINT        NOT NULL DEFAULT 0;

-- Backfill denormalised FK columns from existing join
UPDATE payments p
   SET patient_id = b.patient_id,
       medic_id   = b.medic_id
  FROM bookings b
 WHERE p.booking_id = b.id
   AND p.patient_id IS NULL;

-- Make the columns NOT NULL now that they are populated
ALTER TABLE payments
    ALTER COLUMN patient_id SET NOT NULL,
    ALTER COLUMN medic_id   SET NOT NULL;

-- Performance indices
CREATE INDEX IF NOT EXISTS idx_payments_stripe_pi_id
    ON payments (stripe_payment_intent_id);

CREATE INDEX IF NOT EXISTS idx_payments_stripe_charge_id
    ON payments (stripe_charge_id)
    WHERE stripe_charge_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_state_updated_at
    ON payments (status, updated_at);

-- ── medic_stripe_accounts table ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS medic_stripe_accounts (
    medic_id                        UUID         NOT NULL PRIMARY KEY
        REFERENCES medics(id) ON DELETE CASCADE,
    stripe_account_id               VARCHAR(255) NOT NULL UNIQUE,
    charges_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    payouts_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    details_submitted               BOOLEAN      NOT NULL DEFAULT FALSE,
    requirements_currently_due_json TEXT,
    account_status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    last_synced_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_medic_stripe_accounts_account_id
    ON medic_stripe_accounts (stripe_account_id);

-- ── webhook_events table ──────────────────────────────────────────────────────
-- Idempotency ledger: one row per Stripe event id; PK = stripe_event_id.
-- We intentionally do NOT store the full payload to avoid persisting PAN-like data.

CREATE TABLE IF NOT EXISTS webhook_events (
    stripe_event_id VARCHAR(255) NOT NULL PRIMARY KEY,
    type            VARCHAR(100) NOT NULL,
    status          VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_type
    ON webhook_events (type);

CREATE INDEX IF NOT EXISTS idx_webhook_events_received_at
    ON webhook_events (received_at);

CREATE INDEX IF NOT EXISTS idx_webhook_events_status
    ON webhook_events (status);

-- ── medics / clinics — stripe account status columns ─────────────────────────

ALTER TABLE medics
    ADD COLUMN IF NOT EXISTS stripe_charges_enabled    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stripe_payouts_enabled    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stripe_account_status     VARCHAR(20) DEFAULT 'PENDING';

ALTER TABLE clinics
    ADD COLUMN IF NOT EXISTS stripe_charges_enabled    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stripe_payouts_enabled    BOOLEAN     DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stripe_account_status     VARCHAR(20) DEFAULT 'PENDING';
