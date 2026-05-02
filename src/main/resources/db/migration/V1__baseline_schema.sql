-- ============================================================
-- V1 — Complete baseline schema
-- ============================================================
-- Creates all application tables from scratch on a fresh database.
-- All Stripe columns are included from the start so no subsequent
-- ALTER TABLE migrations are needed for the initial schema.
-- ============================================================

-- ── users ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    role               VARCHAR(30)  NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    stripe_customer_id VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- ── clinics ───────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS clinics (
    id                UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(255)  NOT NULL,
    cui               VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING_APPROVAL',
    stripe_account_id VARCHAR(255),
    commission_rate   NUMERIC(5, 4) NOT NULL DEFAULT 0.1500,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_clinics_cui UNIQUE (cui)
);

-- ── specialties ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS specialties (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    CONSTRAINT uk_specialties_name UNIQUE (name)
);

-- ── medics ────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS medics (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id                  UUID         NOT NULL,
    clinic_id                UUID,
    license_number           VARCHAR(255) NOT NULL,
    license_expires_at       DATE,
    verification_status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING_DOCUMENTS',
    stripe_account_id        VARCHAR(255),
    is_available_for_instant BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_medics_user_id  UNIQUE (user_id),
    CONSTRAINT uk_medics_license  UNIQUE (license_number),
    CONSTRAINT fk_medics_user     FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_medics_clinic   FOREIGN KEY (clinic_id) REFERENCES clinics(id)
);

-- ── medic_specialties ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS medic_specialties (
    medic_id     UUID NOT NULL,
    specialty_id UUID NOT NULL,
    CONSTRAINT pk_medic_specialties  PRIMARY KEY (medic_id, specialty_id),
    CONSTRAINT fk_ms_medic           FOREIGN KEY (medic_id)     REFERENCES medics(id)     ON DELETE CASCADE,
    CONSTRAINT fk_ms_specialty       FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE CASCADE
);

-- ── availabilities ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS availabilities (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    medic_id          UUID        NOT NULL,
    day_of_week       VARCHAR(15) NOT NULL,
    start_time        TIME        NOT NULL,
    end_time          TIME        NOT NULL,
    slot_duration_min INT         NOT NULL,
    buffer_min        INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_availabilities_medic FOREIGN KEY (medic_id) REFERENCES medics(id) ON DELETE CASCADE
);

-- ── slots ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS slots (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    medic_id  UUID        NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    status    VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT fk_slots_medic FOREIGN KEY (medic_id) REFERENCES medics(id) ON DELETE CASCADE
);

-- ── bookings ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS bookings (
    id                              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    patient_id                      UUID        NOT NULL,
    medic_id                        UUID        NOT NULL,
    slot_id                         UUID        NOT NULL,
    consultation_type               VARCHAR(15) NOT NULL,
    payment_status                  VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    cancellation_policy_accepted_at TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bookings_slot_id UNIQUE (slot_id),
    CONSTRAINT fk_bookings_patient FOREIGN KEY (patient_id) REFERENCES users(id),
    CONSTRAINT fk_bookings_medic   FOREIGN KEY (medic_id)   REFERENCES medics(id),
    CONSTRAINT fk_bookings_slot    FOREIGN KEY (slot_id)    REFERENCES slots(id)
);

-- ── payments ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS payments (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id               UUID           NOT NULL,
    patient_id               UUID           NOT NULL,
    medic_id                 UUID           NOT NULL,
    stripe_payment_intent_id VARCHAR(255)   NOT NULL,
    stripe_charge_id         VARCHAR(255),
    stripe_transfer_id       VARCHAR(255),
    stripe_refund_id         VARCHAR(255),
    stripe_dispute_id        VARCHAR(255),
    amount                   NUMERIC(12, 2) NOT NULL,
    amount_bani              BIGINT         NOT NULL,
    platform_fee             NUMERIC(12, 2) NOT NULL,
    application_fee_bani     BIGINT         NOT NULL,
    currency                 VARCHAR(3)     NOT NULL DEFAULT 'RON',
    status                   VARCHAR(15)    NOT NULL DEFAULT 'RESERVED',
    released_at              TIMESTAMPTZ,
    refunded_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_payments_booking    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX IF NOT EXISTS idx_payments_status
    ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_booking_id
    ON payments (booking_id);
CREATE INDEX IF NOT EXISTS idx_payments_stripe_pi_id
    ON payments (stripe_payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_payments_stripe_charge_id
    ON payments (stripe_charge_id) WHERE stripe_charge_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_state_updated_at
    ON payments (status, updated_at);

-- ── consultations ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS consultations (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id       UUID        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    video_room_id    VARCHAR(255),
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    duration_seconds INT,
    notes_encrypted  TEXT,
    release_at       TIMESTAMPTZ,
    CONSTRAINT uk_consultations_booking_id UNIQUE (booking_id),
    CONSTRAINT fk_consultations_booking    FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX IF NOT EXISTS idx_consultations_release_at
    ON consultations (release_at);

-- ── prescriptions ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS prescriptions (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    consultation_id   UUID        NOT NULL,
    content_encrypted TEXT        NOT NULL,
    pdf_s3_key        VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_prescriptions_consultation_id UNIQUE (consultation_id),
    CONSTRAINT fk_prescriptions_consultation    FOREIGN KEY (consultation_id) REFERENCES consultations(id)
);

-- ── disputes ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS disputes (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    consultation_id UUID        NOT NULL,
    raised_by       UUID        NOT NULL,
    resolved_by     UUID,
    reason          TEXT        NOT NULL,
    status          VARCHAR(25) NOT NULL DEFAULT 'OPEN',
    resolved_at     TIMESTAMPTZ,
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_disputes_consultation FOREIGN KEY (consultation_id) REFERENCES consultations(id),
    CONSTRAINT fk_disputes_raised_by    FOREIGN KEY (raised_by)       REFERENCES users(id),
    CONSTRAINT fk_disputes_resolved_by  FOREIGN KEY (resolved_by)     REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_disputes_status
    ON disputes (status);
CREATE INDEX IF NOT EXISTS idx_disputes_consultation_id
    ON disputes (consultation_id);

-- ── medic_invitations ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS medic_invitations (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    clinic_id   UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    token       VARCHAR(64)  NOT NULL,
    status      VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    CONSTRAINT uk_medic_invitations_token   UNIQUE (token),
    CONSTRAINT fk_medic_invitations_clinic  FOREIGN KEY (clinic_id) REFERENCES clinics(id)
);

CREATE INDEX IF NOT EXISTS idx_medic_invitations_token
    ON medic_invitations (token);

-- ── medic_stripe_accounts ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS medic_stripe_accounts (
    medic_id                        UUID         NOT NULL PRIMARY KEY,
    stripe_account_id               VARCHAR(255) NOT NULL,
    charges_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    payouts_enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    details_submitted               BOOLEAN      NOT NULL DEFAULT FALSE,
    requirements_currently_due_json TEXT,
    account_status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    last_synced_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_medic_stripe_accounts_account_id UNIQUE (stripe_account_id),
    CONSTRAINT fk_medic_stripe_accounts_medic      FOREIGN KEY (medic_id) REFERENCES medics(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_medic_stripe_accounts_account_id
    ON medic_stripe_accounts (stripe_account_id);

-- ── webhook_events ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS webhook_events (
    stripe_event_id VARCHAR(255) NOT NULL PRIMARY KEY,
    type            VARCHAR(100) NOT NULL,
    status          VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
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

-- ── audit_logs ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID        NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_id
    ON audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs (created_at);
