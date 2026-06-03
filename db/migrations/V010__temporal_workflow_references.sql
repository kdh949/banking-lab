-- Persist Temporal workflow references on domain-owned case rows.
-- Temporal is the long-running workflow engine; these columns make worker state visible to staff/admin screens.

ALTER TABLE complaint_cases
  ADD COLUMN temporal_workflow_id TEXT,
  ADD COLUMN temporal_run_id TEXT;

ALTER TABLE fds_cases
  ADD COLUMN temporal_workflow_id TEXT,
  ADD COLUMN temporal_run_id TEXT;

ALTER TABLE aml_cases
  ADD COLUMN temporal_workflow_id TEXT,
  ADD COLUMN temporal_run_id TEXT;

ALTER TABLE reconciliation_adjustment_requests
  ADD COLUMN temporal_workflow_id TEXT,
  ADD COLUMN temporal_run_id TEXT;

ALTER TABLE account_holds
  ADD COLUMN temporal_workflow_id TEXT,
  ADD COLUMN temporal_run_id TEXT;

CREATE INDEX idx_complaint_cases_temporal_workflow ON complaint_cases(temporal_workflow_id);
CREATE INDEX idx_fds_cases_temporal_workflow ON fds_cases(temporal_workflow_id);
CREATE INDEX idx_aml_cases_temporal_workflow ON aml_cases(temporal_workflow_id);
CREATE INDEX idx_reconciliation_adjustments_temporal_workflow ON reconciliation_adjustment_requests(temporal_workflow_id);
CREATE INDEX idx_account_holds_temporal_workflow ON account_holds(temporal_workflow_id);
