-- ============================================================
-- Dev script — start-now consultation
-- ============================================================
-- Creates a single, ready-to-start consultation between:
--   • Medic   : Andrei Ionescu  (dr.ionescu@clinic.ro)
--                medic_id  c1000000-0000-0000-0000-000000000001
--                user_id   b1000000-0000-0000-0000-000000000002
--   • Patient : Maria Popescu   (maria.popescu@example.com)
--                user_id   b1000000-0000-0000-0000-000000000001
--
-- The slot starts 1 minute ago and ends in 29 minutes, so the
-- medic's join window (-5 min → +30 min) is open right now.
-- The consultation is left in SCHEDULED so the medic can press
-- "Start Consultation" (POST /consultations/start) to move it
-- to IN_PROGRESS and capture the payment escrow.
--
-- Uses the ...0099 UUID family so it never collides with the
-- V6/V7 dev-seed rows. Re-running first clears the prior copy,
-- then re-inserts with a fresh NOW() — safe to run repeatedly.
-- Prerequisite: V6 dev seed (the two users + medic profile).
-- ============================================================

BEGIN;

-- ── Clean any previous run of THIS script (child → parent order) ──────────────
DELETE FROM consultations WHERE id = '20000000-0000-0000-0000-000000000099';
DELETE FROM payments      WHERE id = 'f1000000-0000-0000-0000-000000000099';
DELETE FROM bookings      WHERE id = 'e1000000-0000-0000-0000-000000000099';
DELETE FROM slots         WHERE id = 'd1000000-0000-0000-0000-000000000099';

-- ── Slot (starts ~now, BOOKED) ────────────────────────────────────────────────
INSERT INTO slots (id, medic_id, starts_at, ends_at, status) VALUES
    ('d1000000-0000-0000-0000-000000000099',
     'c1000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '1 minute',
     NOW() - INTERVAL '1 minute' + INTERVAL '30 minutes',
     'BOOKED');

-- ── Booking (VIDEO, PAID) ─────────────────────────────────────────────────────
INSERT INTO bookings (id, patient_id, medic_id, slot_id, consultation_type,
                      payment_status, cancellation_policy_accepted_at, created_at) VALUES
    ('e1000000-0000-0000-0000-000000000099',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000099',
     'VIDEO', 'PAID',
     NOW() - INTERVAL '2 minutes',
     NOW() - INTERVAL '2 minutes');

-- ── Payment (RESERVED — captured to HELD when the medic starts) ───────────────
-- 150 RON gross, 22.50 RON platform fee (15%), 127.50 RON net
INSERT INTO payments (id, booking_id, patient_id, medic_id,
                      stripe_payment_intent_id, amount, amount_bani,
                      platform_fee, application_fee_bani,
                      currency, status, released_at, created_at, updated_at, version) VALUES
    ('f1000000-0000-0000-0000-000000000099',
     'e1000000-0000-0000-0000-000000000099',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_now_099', 150.00, 15000, 22.50, 2250,
     'RON', 'RESERVED', NULL,
     NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '2 minutes', 0);

-- ── Consultation (SCHEDULED — ready to start) ─────────────────────────────────
INSERT INTO consultations (id, booking_id, status,
                           video_room_id, video_room_url, video_provider,
                           started_at, ended_at, duration_seconds, release_at) VALUES
    ('20000000-0000-0000-0000-000000000099',
     'e1000000-0000-0000-0000-000000000099',
     'SCHEDULED',
     'room-seed-now-099',
     'https://mediconnect.daily.co/room-seed-now-099',
     'daily',
     NULL, NULL, NULL, NULL);

COMMIT;
