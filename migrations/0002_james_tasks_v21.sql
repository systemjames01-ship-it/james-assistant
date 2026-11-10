-- Jâmes v2.1 persistent task list.
-- Additive migration: leaves all existing conversation and memory tables untouched.
CREATE TABLE IF NOT EXISTS james_tasks_v21 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL,
  title TEXT NOT NULL,
  details TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'completed')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  completed_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_james_tasks_v21_session_status
ON james_tasks_v21 (session_id, status, id);
