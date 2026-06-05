-- Retry and dead-letter state for synthetic reporting outbox publication.

ALTER TABLE reporting_outbox_events
  DROP CONSTRAINT IF EXISTS reporting_outbox_events_status_check;

ALTER TABLE reporting_outbox_events
  ADD CONSTRAINT chk_reporting_outbox_events_status
  CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'));

ALTER TABLE reporting_outbox_events
  ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  ADD COLUMN next_retry_at TIMESTAMPTZ,
  ADD COLUMN error_message TEXT;

CREATE INDEX idx_reporting_outbox_status_retry
  ON reporting_outbox_events(status, next_retry_at, created_at);
