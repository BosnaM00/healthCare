-- ============================================================
-- V8 — Backfill video_room_url for V7 dev-seed consultations
-- ============================================================
-- V7 seeded rows with a video_room_id but left video_room_url
-- NULL because the column was added later.  The URL pattern
-- matches what DailyVideoProvider produces in stub mode:
--   https://<daily.domain>/<roomName>
-- Only updates rows where the URL is still missing so this
-- migration is safe to apply on both fresh and existing DBs.
-- ============================================================

UPDATE consultations
SET    video_room_url = 'https://mediconnect.daily.co/' || video_room_id
WHERE  video_room_id  LIKE 'room-seed-%'
  AND  video_room_url IS NULL;
