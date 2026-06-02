-- Durable event backbone tables. Publisher/consumer workers are introduced in Phase 2.

CREATE TABLE outbox_events (
  outbox_event_id TEXT PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  payload_json JSONB NOT NULL,
  headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
  retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  next_retry_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  error_message TEXT,
  CONSTRAINT outbox_domain_event_once UNIQUE (aggregate_id, event_type, idempotency_key)
);

CREATE TABLE inbox_events (
  inbox_event_id TEXT PRIMARY KEY,
  consumer_name TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT inbox_consumer_event_once UNIQUE (consumer_name, source_event_id)
);

CREATE INDEX idx_outbox_events_status_retry ON outbox_events(status, next_retry_at, created_at);
CREATE INDEX idx_inbox_events_consumer_processed ON inbox_events(consumer_name, processed_at);
