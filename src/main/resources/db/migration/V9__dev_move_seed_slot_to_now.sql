-- V9 — Dev: move the first upcoming seed slot to start in 2 minutes so the
--           join window (-5 min → +30 min) is open during local testing.
--           Also updates the linked booking's created_at so timelines look sane.
--           Safe to run on any environment — only targets the fixed seed UUID.

UPDATE slots
SET    starts_at = NOW() - INTERVAL '1 minute',
       ends_at   = NOW() - INTERVAL '1 minute' + INTERVAL '30 minutes'
WHERE  id = 'd1000000-0000-0000-0000-000000000005';

UPDATE bookings
SET    created_at = NOW() - INTERVAL '2 minutes'
WHERE  id = 'e1000000-0000-0000-0000-000000000005';

UPDATE consultations
SET    started_at = NULL,
       ended_at   = NULL,
       status     = 'SCHEDULED'
WHERE  booking_id = 'e1000000-0000-0000-0000-000000000005'
  AND  status     = 'SCHEDULED';
