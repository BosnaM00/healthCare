-- ============================================================
-- V4 — Video consultation schema extensions
-- ============================================================
-- Adds video-provider columns to consultations, a source
-- discriminator to webhook_events, and two new tables:
--   video_session_events  — append-only webhook/heartbeat history
--   video_meeting_tokens  — audit + revocation ledger for issued tokens
-- ============================================================

-- ── webhook_events: add source discriminator ─────────────────────────────────
-- Lets Stripe and Daily events coexist in the same idempotency ledger
-- while keeping queries clean.

ALTER TABLE webhook_events
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'stripe';

-- ── consultations: video columns ─────────────────────────────────────────────

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS video_room_url        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS video_provider        VARCHAR(32)  NOT NULL DEFAULT 'daily',
    ADD COLUMN IF NOT EXISTS video_room_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failure_reason        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS first_joined_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_left_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS recording_s3_key      VARCHAR(512);

ALTER TABLE consultations
    ADD CONSTRAINT chk_failure_reason
        CHECK (failure_reason IS NULL OR failure_reason IN (
            'MEDIC_NO_SHOW', 'PATIENT_NO_SHOW', 'TECHNICAL_FAILURE',
            'MUTUAL_CANCEL', 'OTHER'
        ));

-- ── video_session_events ──────────────────────────────────────────────────────
-- Append-only history of every Daily webhook event and heartbeat sample
-- received for a consultation. Used by the diagnostics endpoint and
-- the dispute UI to reconstruct what happened in a call.

CREATE TABLE IF NOT EXISTS video_session_events (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    consultation_id UUID        NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,   -- e.g. meeting.started, participant.joined, heartbeat
    actor_user_id   UUID,                   -- nullable; webhook may not name a specific user
    payload         JSONB       NOT NULL,   -- raw event payload (webhook body or heartbeat data)
    occurred_at     TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_vse_consultation
    ON video_session_events(consultation_id, occurred_at);

-- ── video_meeting_tokens ──────────────────────────────────────────────────────
-- Minimal record of every token issued via POST /consultations/{id}/join.
-- Used for audit (GDPR token-issuance log) and JTI-based revocation.

CREATE TABLE IF NOT EXISTS video_meeting_tokens (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    consultation_id UUID        NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id),
    role            VARCHAR(16) NOT NULL,       -- OWNER | PARTICIPANT
    token_jti       VARCHAR(64) NOT NULL UNIQUE, -- claim id attached for revocation
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ                 -- NULL = still valid
);

CREATE INDEX IF NOT EXISTS ix_vmt_consultation
    ON video_meeting_tokens(consultation_id);
