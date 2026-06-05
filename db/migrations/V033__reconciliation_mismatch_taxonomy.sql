-- Durable synthetic external-feed mismatch taxonomy for reconciliation items.
-- These columns classify simulator discrepancies without importing real clearing data.

ALTER TABLE reconciliation_items
  ADD COLUMN mismatch_type TEXT NOT NULL DEFAULT 'AMOUNT_MISMATCH',
  ADD COLUMN internal_amount_minor BIGINT,
  ADD COLUMN external_amount_minor BIGINT,
  ADD COLUMN external_status TEXT,
  ADD COLUMN feed_file_id TEXT,
  ADD COLUMN detected_reason TEXT NOT NULL DEFAULT 'Synthetic reconciliation mismatch detected';

ALTER TABLE reconciliation_items
  ADD CONSTRAINT reconciliation_items_mismatch_type_check
  CHECK (
    mismatch_type IN (
      'AMOUNT_MISMATCH',
      'MISSING_EXTERNAL',
      'UNEXPECTED_EXTERNAL',
      'DUPLICATE_EXTERNAL',
      'STALE_EXTERNAL',
      'STATUS_MISMATCH'
    )
  );

CREATE INDEX idx_reconciliation_items_mismatch_type
  ON reconciliation_items(mismatch_type, business_date, status);
