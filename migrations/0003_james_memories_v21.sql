-- Jâmes v2.2 explicit long-term memory.
-- Additive only: never alters conversation or task data.
CREATE TABLE IF NOT EXISTS james_memories_v21 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL,
  memory_key TEXT NOT NULL,
  memory_value TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (session_id, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_james_memories_v21_session
ON james_memories_v21 (session_id, updated_at DESC);
