-- Class marks comparison — store one row per (student, class). Anonymized.
-- anon_id = "p_" + abs(hash(rollNumber)).toString(16) — same scheme used by
-- chess and Crashlytics user grouping. Never stores the raw roll number.
--
-- class_key = "{batchYear}_{deptShort}_{section}_{sem}" e.g. "2024_CSE_A_3".
-- subjects = JSON map of subject code -> {ca1, ca2, ca3, total, status}.
-- overall_avg = mean total% across all subjects with status != "NOT_ENTERED".
-- uploaded_at = unix epoch ms.

CREATE TABLE IF NOT EXISTS class_marks (
  anon_id      TEXT PRIMARY KEY,
  class_key    TEXT NOT NULL,
  subjects     TEXT NOT NULL,
  overall_avg  REAL NOT NULL,
  uploaded_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_class_key ON class_marks(class_key);
CREATE INDEX IF NOT EXISTS idx_uploaded_at ON class_marks(uploaded_at);
