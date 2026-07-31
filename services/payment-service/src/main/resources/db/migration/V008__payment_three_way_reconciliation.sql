
CREATE TABLE payment_reconciliation_runs (
  payment_reconciliation_run_id TEXT PRIMARY KEY,
  external_settlement_import_id TEXT NOT NULL UNIQUE
    REFERENCES payment_external_settlement_imports(external_settlement_import_id),
  business_date DATE NOT NULL,
  expected_value_date DATE NOT NULL CHECK (expected_value_date >= business_date),
  allowed_value_date_lag_days INTEGER NOT NULL CHECK (allowed_value_date_lag_days BETWEEN 0 AND 30),
  sla_days INTEGER NOT NULL CHECK (sla_days BETWEEN 1 AND 30),
  owner_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('EVIDENCE_PENDING', 'MATCHED', 'EXCEPTIONS_OPEN', 'FAILED')),
  idempotency_key TEXT NOT NULL UNIQUE,
  request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  external_line_count INTEGER NOT NULL DEFAULT 0 CHECK (external_line_count >= 0),
  ledger_evidence_count INTEGER NOT NULL DEFAULT 0 CHECK (ledger_evidence_count >= 0),
  matched_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_count >= 0),
  exception_count INTEGER NOT NULL DEFAULT 0 CHECK (exception_count >= 0),
  claim_token TEXT,
  claim_expires_at TIMESTAMPTZ,
  attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
  failure_message TEXT,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  CONSTRAINT payment_reconciliation_claim_state CHECK (
    (status = 'EVIDENCE_PENDING' AND claim_token IS NOT NULL AND claim_expires_at IS NOT NULL AND completed_at IS NULL)
    OR
    (status IN ('MATCHED', 'EXCEPTIONS_OPEN', 'FAILED') AND claim_token IS NULL AND claim_expires_at IS NULL)
  ),
  CONSTRAINT payment_reconciliation_terminal_counts CHECK (
    status IN ('EVIDENCE_PENDING', 'FAILED')
    OR matched_count + exception_count >= 0
  )
);

CREATE TABLE payment_reconciliation_results (
  payment_reconciliation_result_id TEXT PRIMARY KEY,
  payment_reconciliation_run_id TEXT NOT NULL
    REFERENCES payment_reconciliation_runs(payment_reconciliation_run_id),
  external_settlement_line_id TEXT
    REFERENCES payment_external_settlement_lines(external_settlement_line_id),
  payment_instruction_id TEXT NOT NULL CHECK (payment_instruction_id LIKE 'PAY-%'),
  ledger_transaction_id TEXT,
  mismatch_type TEXT NOT NULL CHECK (mismatch_type IN (
    'MATCHED',
    'MISSING_PAYMENT',
    'MISSING_LEDGER',
    'MISSING_EXTERNAL',
    'AMOUNT_MISMATCH',
    'STATUS_MISMATCH',
    'DUPLICATE_EXTERNAL',
    'VALUE_DATE_MISMATCH',
    'LATE_SETTLEMENT'
  )),
  result_status TEXT NOT NULL CHECK (result_status IN ('MATCHED', 'OPEN', 'INVESTIGATING', 'RESOLVED')),
  internal_amount_minor BIGINT CHECK (internal_amount_minor IS NULL OR internal_amount_minor > 0),
  ledger_amount_minor BIGINT CHECK (ledger_amount_minor IS NULL OR ledger_amount_minor > 0),
  external_amount_minor BIGINT CHECK (external_amount_minor IS NULL OR external_amount_minor > 0),
  currency TEXT CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
  external_status TEXT CHECK (external_status IS NULL OR external_status IN ('ACCEPTED', 'REJECTED', 'RETURNED')),
  business_date DATE NOT NULL,
  expected_value_date DATE NOT NULL,
  actual_value_date DATE,
  owner_id TEXT NOT NULL,
  detected_reason TEXT NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,
  due_at TIMESTAMPTZ,
  resolution TEXT,
  approval_id TEXT,
  resolved_at TIMESTAMPTZ,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  CONSTRAINT payment_reconciliation_result_state CHECK (
    (mismatch_type = 'MATCHED' AND result_status = 'MATCHED' AND due_at IS NULL)
    OR
    (mismatch_type <> 'MATCHED' AND result_status IN ('OPEN', 'INVESTIGATING', 'RESOLVED') AND due_at IS NOT NULL)
  ),
  CONSTRAINT payment_reconciliation_resolution_state CHECK (
    (result_status = 'RESOLVED' AND resolved_at IS NOT NULL AND resolution IS NOT NULL)
    OR
    (result_status <> 'RESOLVED' AND resolved_at IS NULL)
  )
);

CREATE UNIQUE INDEX uq_payment_reconciliation_external_line
  ON payment_reconciliation_results(payment_reconciliation_run_id, external_settlement_line_id)
  WHERE external_settlement_line_id IS NOT NULL;

CREATE UNIQUE INDEX uq_payment_reconciliation_missing_external
  ON payment_reconciliation_results(payment_reconciliation_run_id, payment_instruction_id, mismatch_type)
  WHERE external_settlement_line_id IS NULL;

CREATE INDEX idx_payment_reconciliation_results_queue
  ON payment_reconciliation_results(result_status, due_at, owner_id, mismatch_type);

CREATE TABLE payment_reconciliation_access_audit_events (
  reconciliation_access_audit_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL CHECK (event_type IN (
    'PAYMENT_RECONCILIATION_RUN_VIEW',
    'PAYMENT_RECONCILIATION_EXCEPTION_LIST_VIEW'
  )),
  payment_reconciliation_run_id TEXT
    REFERENCES payment_reconciliation_runs(payment_reconciliation_run_id),
  actor_id TEXT NOT NULL,
  actor_roles TEXT NOT NULL,
  reason TEXT NOT NULL,
  result_count INTEGER NOT NULL CHECK (result_count >= 0),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_reconciliation_access_audit_created
  ON payment_reconciliation_access_audit_events(created_at DESC, actor_id);

COMMENT ON TABLE payment_reconciliation_runs IS
  'Three-way comparison of Payment Service instruction state, core-banking bill-payment evidence, and an independent external clearing import.';

COMMENT ON COLUMN payment_reconciliation_results.due_at IS
  'Exception remediation SLA deadline. MATCHED results have no due_at.';

COMMENT ON COLUMN payment_reconciliation_results.approval_id IS
  'Reserved for the next maker-checker settlement adjustment slice; reconciliation detection never self-approves a correction.';

COMMENT ON COLUMN payment_reconciliation_results.resolution IS
  'Human-readable resolution is populated only by a later controlled resolution workflow.';
