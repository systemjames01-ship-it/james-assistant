-- Additive migration: shared profile conversation index and per-IP request limits.
-- Existing conversation messages, memories, tasks and user data are not modified.

CREATE TABLE IF NOT EXISTS james_profile_conversations_v21 (
  profile_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT 'Conversa',
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (profile_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_james_profile_conversations_profile_updated
  ON james_profile_conversations_v21 (profile_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS james_request_rate_limits (
  bucket_key TEXT PRIMARY KEY,
  window_started_at INTEGER NOT NULL,
  request_count INTEGER NOT NULL
);
