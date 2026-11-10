-- Jâmes v2.1 conversation history table.
-- Additive migration: does not modify or delete the legacy james_memory table
-- or any previous conversation_messages table.
CREATE TABLE IF NOT EXISTS james_conversation_messages_v21 (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('user', 'model')),
  content TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_james_conversation_messages_v21_session_id
ON james_conversation_messages_v21 (session_id, id);
