-- Synthetic authentication hardening controls for H2.
-- Device fingerprints and sessions are lab-only simulators, not real device intelligence or real PII.

CREATE TABLE trusted_devices (
  trusted_device_id TEXT PRIMARY KEY,
  actor_type TEXT NOT NULL CHECK (actor_type IN ('CUSTOMER', 'STAFF')),
  actor_id TEXT NOT NULL,
  customer_id TEXT,
  device_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
  registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  last_seen_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT trusted_devices_customer_scope CHECK (
    actor_type <> 'CUSTOMER' OR customer_id IS NOT NULL
  ),
  CONSTRAINT trusted_devices_unique_actor_device UNIQUE (actor_type, actor_id, device_fingerprint)
);

CREATE TABLE revoked_sessions (
  session_id TEXT PRIMARY KEY,
  actor_id TEXT NOT NULL,
  actor_type TEXT NOT NULL CHECK (actor_type IN ('CUSTOMER', 'STAFF')),
  revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  expires_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_trusted_devices_actor_status
  ON trusted_devices(actor_type, actor_id, status);

CREATE INDEX idx_revoked_sessions_actor
  ON revoked_sessions(actor_type, actor_id, revoked_at);
