-- Synthetic audit export jobs.
-- Exports are local lab artifacts only. They do not store real PII, real bank data,
-- real payment-network data, or real external-provider data.

CREATE TABLE audit_export_jobs (
  export_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'EXPORTED', 'REJECTED')),
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL CHECK (requested_role IN ('AUDITOR', 'COMPLIANCE_MANAGER')),
  reason TEXT NOT NULL,
  export_format TEXT NOT NULL CHECK (export_format IN ('NDJSON')),
  approval_id TEXT NOT NULL UNIQUE REFERENCES operator_approvals(approval_id),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_by TEXT,
  approved_role TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_role TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,
  from_audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  through_audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  row_count INTEGER NOT NULL DEFAULT 0 CHECK (row_count >= 0),
  hash_chain_start TEXT,
  hash_chain_end TEXT,
  payload_sha256 TEXT,
  storage_uri TEXT,
  ledger_rows_mutated BOOLEAN NOT NULL DEFAULT FALSE CHECK (ledger_rows_mutated = FALSE),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic_only = TRUE),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT audit_export_checker_different CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

CREATE INDEX idx_audit_export_jobs_status_requested
  ON audit_export_jobs(status, requested_at);

CREATE TABLE audit_export_files (
  file_id TEXT PRIMARY KEY,
  export_id TEXT NOT NULL UNIQUE REFERENCES audit_export_jobs(export_id) ON DELETE CASCADE,
  file_name TEXT NOT NULL,
  format TEXT NOT NULL CHECK (format IN ('NDJSON')),
  row_count INTEGER NOT NULL CHECK (row_count >= 0),
  sha256 TEXT NOT NULL,
  storage_uri TEXT NOT NULL,
  content_jsonl TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic_only = TRUE)
);

CREATE INDEX idx_audit_export_files_created
  ON audit_export_files(created_at);
