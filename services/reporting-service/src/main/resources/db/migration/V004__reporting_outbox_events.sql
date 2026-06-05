-- Durable reporting outbox for synthetic report artifact domain events.
-- Events are persisted in the same transaction as reporting metadata changes.

CREATE TABLE reporting_outbox_events (
  outbox_event_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  idempotency_key TEXT,
  payload_json JSONB NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
  synthetic_only BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_reporting_outbox_idempotency
  ON reporting_outbox_events(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_reporting_outbox_status_created
  ON reporting_outbox_events(status, created_at);
