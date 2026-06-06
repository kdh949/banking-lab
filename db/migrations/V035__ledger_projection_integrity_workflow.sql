-- Ledger projection integrity operations.
-- ledger_transactions and ledger_postings remain source-of-truth rows; rebuilds only repair account_balance_projections.

CREATE TABLE ledger_projection_drift_runs (
  run_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  account_id TEXT REFERENCES accounts(account_id),
  currency CHAR(3),
  as_of_business_date DATE,
  source_posting_count BIGINT NOT NULL DEFAULT 0 CHECK (source_posting_count >= 0),
  source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  source_hash TEXT NOT NULL,
  drift_item_count INTEGER NOT NULL DEFAULT 0 CHECK (drift_item_count >= 0),
  audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE ledger_projection_drift_items (
  item_id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL REFERENCES ledger_projection_drift_runs(run_id) ON DELETE CASCADE,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  currency CHAR(3) NOT NULL,
  expected_ledger_balance_minor BIGINT NOT NULL,
  actual_ledger_balance_minor BIGINT,
  expected_available_balance_minor BIGINT NOT NULL,
  actual_available_balance_minor BIGINT,
  hold_amount_minor BIGINT NOT NULL CHECK (hold_amount_minor >= 0),
  drift_amount_minor BIGINT NOT NULL,
  source_posting_count BIGINT NOT NULL CHECK (source_posting_count >= 0),
  source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  source_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('DRIFT_DETECTED', 'PROJECTION_MISSING')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (run_id, account_id, currency)
);

CREATE TABLE ledger_projection_rebuild_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  drift_run_id TEXT REFERENCES ledger_projection_drift_runs(run_id),
  account_id TEXT REFERENCES accounts(account_id),
  currency CHAR(3),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED')),
  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),
  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,
  before_source_posting_count BIGINT NOT NULL DEFAULT 0 CHECK (before_source_posting_count >= 0),
  before_source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  before_source_hash TEXT NOT NULL,
  before_projection_hash TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_projection_rebuild_runs (
  run_id TEXT PRIMARY KEY,
  request_id TEXT NOT NULL UNIQUE REFERENCES ledger_projection_rebuild_requests(request_id),
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),
  status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
  executed_by TEXT NOT NULL,
  executed_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  account_id TEXT REFERENCES accounts(account_id),
  currency CHAR(3),
  before_source_posting_count BIGINT NOT NULL CHECK (before_source_posting_count >= 0),
  before_source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  before_source_hash TEXT NOT NULL,
  before_projection_hash TEXT NOT NULL,
  after_source_posting_count BIGINT NOT NULL DEFAULT 0 CHECK (after_source_posting_count >= 0),
  after_source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  after_source_hash TEXT NOT NULL,
  after_projection_hash TEXT NOT NULL,
  rebuilt_item_count INTEGER NOT NULL DEFAULT 0 CHECK (rebuilt_item_count >= 0),
  audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE ledger_projection_rebuild_items (
  item_id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL REFERENCES ledger_projection_rebuild_runs(run_id) ON DELETE CASCADE,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  currency CHAR(3) NOT NULL,
  previous_ledger_balance_minor BIGINT,
  rebuilt_ledger_balance_minor BIGINT NOT NULL,
  previous_available_balance_minor BIGINT,
  rebuilt_available_balance_minor BIGINT NOT NULL,
  hold_amount_minor BIGINT NOT NULL CHECK (hold_amount_minor >= 0),
  drift_amount_minor BIGINT NOT NULL,
  source_posting_count BIGINT NOT NULL CHECK (source_posting_count >= 0),
  source_last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  source_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('REBUILT', 'UNCHANGED', 'PROJECTION_CREATED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (run_id, account_id, currency)
);

CREATE INDEX idx_ledger_projection_drift_runs_created
  ON ledger_projection_drift_runs(created_at DESC);

CREATE INDEX idx_ledger_projection_drift_items_account
  ON ledger_projection_drift_items(account_id, currency);

CREATE INDEX idx_ledger_projection_rebuild_requests_status
  ON ledger_projection_rebuild_requests(status, created_at DESC);

CREATE INDEX idx_ledger_projection_rebuild_runs_request
  ON ledger_projection_rebuild_runs(request_id);
