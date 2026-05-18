-- PWA server-side cron polling — store encrypted SIS credentials so the
-- cron handler can hit SIS on behalf of dormant PWA users.
--
-- anon_id = Firebase UID (same scheme as class_marks). PRIMARY KEY so
-- INSERT OR REPLACE on /pwa/register overwrites cleanly when the user
-- re-registers (e.g. password change, classKey change).
--
-- enc_password = base64(iv || ciphertext) of AES-GCM(roll_number || ":" || password)
-- encrypted with the master key bound to env.PWA_CRED_KEY (Worker secret).
-- The IV is the first 12 bytes after base64-decode; the GCM auth tag is
-- appended to the ciphertext by SubtleCrypto.encrypt.
--
-- class_key = snapshot at registration time. Updated on each register call.
-- The cron uses this to attribute marks to the right class.
--
-- last_synced_at = wall-clock ms of last successful upload to class_marks.
-- last_error = short string from the most recent failure (null on success).

CREATE TABLE IF NOT EXISTS pwa_creds (
  anon_id        TEXT PRIMARY KEY,
  roll_number    TEXT NOT NULL,
  enc_password   TEXT NOT NULL,
  class_key      TEXT NOT NULL,
  created_at     INTEGER NOT NULL,
  last_synced_at INTEGER,
  last_error     TEXT
);

-- Index for the cron's "oldest first" iteration.
CREATE INDEX IF NOT EXISTS idx_pwa_synced ON pwa_creds(last_synced_at);
