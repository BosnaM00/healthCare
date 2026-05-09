-- ============================================================
-- V6 — Dev seed data (local development only)
-- ============================================================
-- Inserts a patient, a medic, and an admin with password = "password"
-- (BCrypt strength 10). Safe to run repeatedly — all inserts use
-- ON CONFLICT DO NOTHING so they are idempotent.
-- ============================================================

-- ── Specialties ───────────────────────────────────────────────────────────────

INSERT INTO specialties (id, name, description) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'General Medicine',  'Primary care and general health consultations'),
    ('a1000000-0000-0000-0000-000000000002', 'Cardiology',        'Heart and cardiovascular system'),
    ('a1000000-0000-0000-0000-000000000003', 'Dermatology',       'Skin, hair, and nail conditions')
ON CONFLICT DO NOTHING;

-- ── Users ─────────────────────────────────────────────────────────────────────
-- Password for all accounts: "password"
-- Hash: $2a$10$/CnYu2v4VLDIw2h6IE2AU.JchNKGTF/Q39Mv29l3svBbrIUg2xoJG

INSERT INTO users (id, email, password_hash, role, status, first_name, last_name) VALUES
    ('b1000000-0000-0000-0000-000000000001',
     'maria.popescu@example.com',
     '$2a$10$/CnYu2v4VLDIw2h6IE2AU.JchNKGTF/Q39Mv29l3svBbrIUg2xoJG',
     'PATIENT', 'ACTIVE', 'Maria', 'Popescu'),

    ('b1000000-0000-0000-0000-000000000002',
     'dr.ionescu@clinic.ro',
     '$2a$10$/CnYu2v4VLDIw2h6IE2AU.JchNKGTF/Q39Mv29l3svBbrIUg2xoJG',
     'MEDIC', 'ACTIVE', 'Andrei', 'Ionescu'),

    ('b1000000-0000-0000-0000-000000000003',
     'admin@mediconnect.ro',
     '$2a$10$/CnYu2v4VLDIw2h6IE2AU.JchNKGTF/Q39Mv29l3svBbrIUg2xoJG',
     'ADMIN', 'ACTIVE', 'Platform', 'Admin')
ON CONFLICT DO NOTHING;

-- ── Medic profile ─────────────────────────────────────────────────────────────

INSERT INTO medics (id, user_id, license_number, license_expires_at, verification_status, is_available_for_instant) VALUES
    ('c1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000002',
     'RO-MED-000001',
     '2028-12-31',
     'VERIFIED',
     TRUE)
ON CONFLICT DO NOTHING;

-- ── Medic specialties ─────────────────────────────────────────────────────────

INSERT INTO medic_specialties (medic_id, specialty_id) VALUES
    ('c1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001'),
    ('c1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;
