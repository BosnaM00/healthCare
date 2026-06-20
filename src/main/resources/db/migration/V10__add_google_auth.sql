-- ── Google OAuth2 support ──────────────────────────────────────────────────
-- 1. Make password_hash nullable so Google-only users can register without a
--    local password. Existing rows are unaffected (their value stays intact).
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

-- 2. Add google_id column to store the Google subject identifier ("sub" claim).
--    VARCHAR(255) is intentionally generous — Google sub values are currently
--    21-digit numeric strings but the spec does not guarantee a fixed length.
ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255);

-- 3. Unique constraint: one Google account maps to at most one MediConnect user.
ALTER TABLE users
    ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);
