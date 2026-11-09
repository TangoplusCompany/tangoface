-- Migration: 0001_init.sql
-- Description: Create sessions and pair_rate_limits tables

-- 1. Sessions Table
CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,                       -- UUID v4
  code TEXT,                                 -- 6-digit pairing code (set to NULL immediately upon successful pairing)
  code_expires_at INTEGER NOT NULL,          -- Pairing code expiration timestamp (created_at + 10 mins)
  status TEXT NOT NULL CHECK(status IN ('CREATED', 'PAIRED', 'UPLOADED', 'PREPROCESSING', 'GPU_RUNNING', 'DONE', 'FAILED')),
  fail_reason TEXT,                          -- Reason if status is FAILED
  session_secret_hash TEXT NOT NULL,         -- SHA-256 hash of session_secret
  pair_token_hash TEXT,                      -- SHA-256 hash of pair_token (set upon pairing)
  upload_key TEXT,                           -- R2 object key: videos/{session_id}/{random}.mp4
  created_at INTEGER NOT NULL,               -- Creation UNIX timestamp in ms
  expires_at INTEGER NOT NULL,               -- Session expiration UNIX timestamp in ms (e.g. 24 hours)
  paired_at INTEGER                          -- Pairing UNIX timestamp in ms
);

-- Index for searching active pairing code
CREATE INDEX IF NOT EXISTS idx_sessions_code ON sessions(code);

-- Index for cleaning up expired sessions
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);

-- 2. Pair Rate Limits Table (IP-based brute-force prevention: 5 attempts per 10 minutes)
CREATE TABLE IF NOT EXISTS pair_rate_limits (
  ip TEXT PRIMARY KEY,                       -- Client IP address (from CF-Connecting-IP)
  attempts INTEGER NOT NULL DEFAULT 1,       -- Failed attempt count in current window
  reset_at INTEGER NOT NULL                  -- Reset timestamp (UNIX ms, 10 mins from first fail)
);

CREATE INDEX IF NOT EXISTS idx_rate_limits_reset_at ON pair_rate_limits(reset_at);
