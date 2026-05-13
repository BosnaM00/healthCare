-- ============================================================
-- V7 — Dev seed: slots, bookings, payments & consultations
-- ============================================================
-- Creates realistic history for dr.ionescu@clinic.ro (medic) and
-- maria.popescu@example.com (patient):
--   • 3 past completed consultations (RELEASED payments)
--   • 1 past consultation still in dispute window (HELD payment)
--   • 2 upcoming scheduled consultations
-- All inserts use ON CONFLICT DO NOTHING — safe to re-run.
-- ============================================================

-- ── Convenience variables via CTEs ────────────────────────────────────────────

-- Fixed UUIDs so the data is deterministic across restarts
-- Medic profile id:  c1000000-0000-0000-0000-000000000001
-- Patient user id:   b1000000-0000-0000-0000-000000000001
-- Medic user id:     b1000000-0000-0000-0000-000000000002

-- ── Slots ─────────────────────────────────────────────────────────────────────
-- Past slots (BOOKED) and upcoming slots (BOOKED)

INSERT INTO slots (id, medic_id, starts_at, ends_at, status) VALUES
    -- Past consultations
    ('d1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days' + INTERVAL '30 minutes',
     'BOOKED'),
    ('d1000000-0000-0000-0000-000000000002',
     'c1000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days' + INTERVAL '30 minutes',
     'BOOKED'),
    ('d1000000-0000-0000-0000-000000000003',
     'c1000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days' + INTERVAL '30 minutes',
     'BOOKED'),
    -- Recent consultation (within 48h dispute window — payment still HELD)
    ('d1000000-0000-0000-0000-000000000004',
     'c1000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '1 day',  NOW() - INTERVAL '1 day'  + INTERVAL '30 minutes',
     'BOOKED'),
    -- Upcoming consultations
    ('d1000000-0000-0000-0000-000000000005',
     'c1000000-0000-0000-0000-000000000001',
     NOW() + INTERVAL '2 days', NOW() + INTERVAL '2 days' + INTERVAL '30 minutes',
     'BOOKED'),
    ('d1000000-0000-0000-0000-000000000006',
     'c1000000-0000-0000-0000-000000000001',
     NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days' + INTERVAL '30 minutes',
     'BOOKED')
ON CONFLICT DO NOTHING;

-- ── Bookings ──────────────────────────────────────────────────────────────────

INSERT INTO bookings (id, patient_id, medic_id, slot_id, consultation_type,
                      payment_status, cancellation_policy_accepted_at, created_at) VALUES
    ('e1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000001',
     'VIDEO', 'PAID', NOW() - INTERVAL '31 days', NOW() - INTERVAL '31 days'),

    ('e1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000002',
     'VIDEO', 'PAID', NOW() - INTERVAL '21 days', NOW() - INTERVAL '21 days'),

    ('e1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000003',
     'VIDEO', 'PAID', NOW() - INTERVAL '11 days', NOW() - INTERVAL '11 days'),

    ('e1000000-0000-0000-0000-000000000004',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000004',
     'VIDEO', 'PAID', NOW() - INTERVAL '25 hours', NOW() - INTERVAL '25 hours'),

    ('e1000000-0000-0000-0000-000000000005',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000005',
     'VIDEO', 'PAID', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

    ('e1000000-0000-0000-0000-000000000006',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'd1000000-0000-0000-0000-000000000006',
     'VIDEO', 'PAID', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- ── Payments ──────────────────────────────────────────────────────────────────
-- 150 RON gross, 22.50 RON platform fee (15%), 127.50 RON net

INSERT INTO payments (id, booking_id, patient_id, medic_id,
                      stripe_payment_intent_id, amount, amount_bani,
                      platform_fee, application_fee_bani,
                      currency, status, released_at, created_at, updated_at, version) VALUES
    -- Past completed — RELEASED
    ('f1000000-0000-0000-0000-000000000001',
     'e1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000001', 150.00, 15000, 22.50, 2250,
     'RON', 'RELEASED', NOW() - INTERVAL '28 days',
     NOW() - INTERVAL '31 days', NOW() - INTERVAL '28 days', 1),

    ('f1000000-0000-0000-0000-000000000002',
     'e1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000002', 150.00, 15000, 22.50, 2250,
     'RON', 'RELEASED', NOW() - INTERVAL '18 days',
     NOW() - INTERVAL '21 days', NOW() - INTERVAL '18 days', 1),

    ('f1000000-0000-0000-0000-000000000003',
     'e1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000003', 150.00, 15000, 22.50, 2250,
     'RON', 'RELEASED', NOW() - INTERVAL '8 days',
     NOW() - INTERVAL '11 days', NOW() - INTERVAL '8 days', 1),

    -- Recent — still HELD (within 48h dispute window)
    ('f1000000-0000-0000-0000-000000000004',
     'e1000000-0000-0000-0000-000000000004',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000004', 150.00, 15000, 22.50, 2250,
     'RON', 'HELD', NULL,
     NOW() - INTERVAL '25 hours', NOW() - INTERVAL '25 hours', 0),

    -- Upcoming — RESERVED (not yet charged)
    ('f1000000-0000-0000-0000-000000000005',
     'e1000000-0000-0000-0000-000000000005',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000005', 150.00, 15000, 22.50, 2250,
     'RON', 'RESERVED', NULL,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 0),

    ('f1000000-0000-0000-0000-000000000006',
     'e1000000-0000-0000-0000-000000000006',
     'b1000000-0000-0000-0000-000000000001',
     'c1000000-0000-0000-0000-000000000001',
     'pi_seed_000006', 150.00, 15000, 22.50, 2250,
     'RON', 'RESERVED', NULL,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 0)
ON CONFLICT DO NOTHING;

-- ── Consultations ─────────────────────────────────────────────────────────────

INSERT INTO consultations (id, booking_id, status, video_room_id, video_room_url, video_provider,
                           started_at, ended_at, duration_seconds, release_at) VALUES
    -- Completed (payment already released)
    ('10000000-0000-0000-0000-000000000001',
     'e1000000-0000-0000-0000-000000000001',
     'COMPLETED', 'room-seed-001', 'https://mediconnect.daily.co/room-seed-001', 'daily',
     NOW() - INTERVAL '30 days',
     NOW() - INTERVAL '30 days' + INTERVAL '28 minutes',
     1680, NOW() - INTERVAL '28 days'),

    ('10000000-0000-0000-0000-000000000002',
     'e1000000-0000-0000-0000-000000000002',
     'COMPLETED', 'room-seed-002', 'https://mediconnect.daily.co/room-seed-002', 'daily',
     NOW() - INTERVAL '20 days',
     NOW() - INTERVAL '20 days' + INTERVAL '25 minutes',
     1500, NOW() - INTERVAL '18 days'),

    ('10000000-0000-0000-0000-000000000003',
     'e1000000-0000-0000-0000-000000000003',
     'COMPLETED', 'room-seed-003', 'https://mediconnect.daily.co/room-seed-003', 'daily',
     NOW() - INTERVAL '10 days',
     NOW() - INTERVAL '10 days' + INTERVAL '30 minutes',
     1800, NOW() - INTERVAL '8 days'),

    -- Completed but still in 48h dispute window
    ('10000000-0000-0000-0000-000000000004',
     'e1000000-0000-0000-0000-000000000004',
     'COMPLETED', 'room-seed-004', 'https://mediconnect.daily.co/room-seed-004', 'daily',
     NOW() - INTERVAL '1 day',
     NOW() - INTERVAL '1 day' + INTERVAL '27 minutes',
     1620, NOW() + INTERVAL '23 hours'),

    -- Upcoming (SCHEDULED)
    ('10000000-0000-0000-0000-000000000005',
     'e1000000-0000-0000-0000-000000000005',
     'SCHEDULED', 'room-seed-005', 'https://mediconnect.daily.co/room-seed-005', 'daily',
     NULL, NULL, NULL, NULL),

    ('10000000-0000-0000-0000-000000000006',
     'e1000000-0000-0000-0000-000000000006',
     'SCHEDULED', 'room-seed-006', 'https://mediconnect.daily.co/room-seed-006', 'daily',
     NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;
