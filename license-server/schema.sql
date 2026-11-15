PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS license_codes (
 id TEXT PRIMARY KEY, code_hash TEXT NOT NULL UNIQUE,
 plan TEXT NOT NULL CHECK(plan IN ('annual','lifetime')),
 status TEXT NOT NULL DEFAULT 'unused' CHECK(status IN ('unused','active','revoked')),
 created_at INTEGER NOT NULL, expires_at INTEGER, device_id TEXT, activated_at INTEGER, note TEXT
);
CREATE TABLE IF NOT EXISTS license_events (
 id TEXT PRIMARY KEY, code_id TEXT, event TEXT NOT NULL, device_id TEXT, created_at INTEGER NOT NULL, detail TEXT,
 FOREIGN KEY(code_id) REFERENCES license_codes(id)
);
CREATE TABLE IF NOT EXISTS rate_limits (
 bucket TEXT NOT NULL, window_start INTEGER NOT NULL, hits INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(bucket,window_start)
);
CREATE INDEX IF NOT EXISTS idx_license_codes_status ON license_codes(status);
CREATE INDEX IF NOT EXISTS idx_license_codes_device ON license_codes(device_id);
CREATE INDEX IF NOT EXISTS idx_license_events_code ON license_events(code_id);
