-- ============================================================
-- V3 — audit_logs table (no-op if already created by V1)
-- ============================================================
-- audit_logs is created in V1 for fresh installs.
-- This migration is retained as a safe no-op for environments
-- where the baseline was applied before audit_logs was added.
-- ============================================================

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
