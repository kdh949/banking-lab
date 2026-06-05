-- Synthetic retention lifecycle for generated report artifacts.
-- Expiration changes only reporting artifact metadata and never mutates ledger/source rows.

ALTER TABLE report_artifacts
  DROP CONSTRAINT IF EXISTS report_artifacts_status_check;

ALTER TABLE report_artifacts
  ADD CONSTRAINT chk_report_artifacts_status
  CHECK (status IN ('GENERATED', 'EXPIRED'));

ALTER TABLE report_artifacts
  ADD COLUMN expired_at TIMESTAMPTZ,
  ADD COLUMN retention_action TEXT;

ALTER TABLE report_artifacts
  ADD CONSTRAINT chk_report_artifacts_expiration_state
  CHECK (
    (status = 'GENERATED' AND expired_at IS NULL)
    OR (status = 'EXPIRED' AND expired_at IS NOT NULL AND retention_action = 'SYNTHETIC_RETENTION_EXPIRED')
  );

CREATE INDEX idx_report_artifacts_retention_until
  ON report_artifacts(retention_until, status);
