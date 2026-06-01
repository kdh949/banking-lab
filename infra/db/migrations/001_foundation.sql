-- Phase 1 foundation schema for the synthetic Banking Lab.
-- This schema is intentionally conservative: ledger source-of-truth rows are append-only,
-- balances are projections, and staff-sensitive access is auditable.

CREATE TABLE customers (
  customer_id TEXT PRIMARY KEY,
  customer_name TEXT NOT NULL,
  customer_grade TEXT NOT NULL,
  risk_grade TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer_kyc_profiles (
  customer_id TEXT PRIMARY KEY REFERENCES customers(customer_id),
  kyc_status TEXT NOT NULL CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REVIEW_REQUIRED', 'REJECTED')),
  source_of_funds_code TEXT NOT NULL,
  transaction_purpose_code TEXT NOT NULL,
  simulated_provider_reference TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
  account_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  account_no TEXT NOT NULL UNIQUE,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'DORMANT', 'HOLD', 'CLOSED')),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_limits (
  account_id TEXT PRIMARY KEY REFERENCES accounts(account_id),
  daily_transfer_limit_minor BIGINT NOT NULL CHECK (daily_transfer_limit_minor >= 0),
  single_transfer_limit_minor BIGINT NOT NULL CHECK (single_transfer_limit_minor >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_holds (
  hold_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  hold_amount_minor BIGINT NOT NULL CHECK (hold_amount_minor > 0),
  reason_code TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('REQUESTED', 'ACTIVE', 'RELEASE_REQUESTED', 'RELEASED')),
  approval_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transactions (
  ledger_transaction_id TEXT PRIMARY KEY,
  transaction_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('REQUESTED', 'POSTED', 'REVERSED', 'REJECTED')),
  requested_by TEXT NOT NULL,
  requested_channel TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at TIMESTAMPTZ,
  original_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT reversal_requires_original CHECK (
    transaction_type <> 'REVERSAL' OR original_transaction_id IS NOT NULL
  )
);

CREATE TABLE ledger_postings (
  ledger_posting_id TEXT PRIMARY KEY,
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  direction TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  posting_type TEXT NOT NULL CHECK (posting_type IN ('PRINCIPAL', 'FEE', 'TAX', 'HOLD', 'REVERSAL', 'OPENING')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_balances (
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  ledger_balance_minor BIGINT NOT NULL,
  available_balance_minor BIGINT NOT NULL,
  hold_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (hold_amount_minor >= 0),
  last_posting_id TEXT REFERENCES ledger_postings(ledger_posting_id),
  version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, currency),
  CONSTRAINT available_not_over_ledger CHECK (available_balance_minor <= ledger_balance_minor)
);

CREATE TABLE idempotency_keys (
  idempotency_key TEXT PRIMARY KEY,
  command_type TEXT NOT NULL,
  command_hash TEXT NOT NULL,
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  response_hash TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE daily_closings (
  business_date DATE PRIMARY KEY,
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED', 'REOPEN_REQUESTED')),
  closed_by TEXT,
  closed_at TIMESTAMPTZ,
  ledger_total_hash TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_items (
  reconciliation_item_id TEXT PRIMARY KEY,
  business_date DATE NOT NULL,
  source_system TEXT NOT NULL,
  internal_reference_id TEXT,
  external_reference_id TEXT,
  amount_minor BIGINT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (
    status IN ('OPEN', 'INVESTIGATING', 'MATCHED', 'ADJUSTMENT_REQUESTED', 'ADJUSTED', 'WAIVED', 'CLOSED')
  ),
  owner_id TEXT,
  approval_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT open_item_requires_owner CHECK (status <> 'INVESTIGATING' OR owner_id IS NOT NULL)
);

CREATE TABLE audit_events (
  audit_event_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  actor_type TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  branch_id TEXT,
  screen_id TEXT,
  business_reference_id TEXT,
  customer_id TEXT,
  account_id TEXT,
  reason TEXT,
  ip_address INET,
  user_agent TEXT,
  device_id TEXT,
  before_hash TEXT,
  payload_hash TEXT NOT NULL,
  previous_event_hash TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE operator_approvals (
  approval_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  requested_by TEXT NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  request_reason TEXT NOT NULL,
  before_snapshot_json JSONB,
  after_snapshot_json JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,
  audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  CONSTRAINT maker_checker_different CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

CREATE TABLE operator_sessions (
  operator_session_id TEXT PRIMARY KEY,
  operator_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  branch_id TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ
);

CREATE TABLE screen_access_logs (
  screen_access_log_id TEXT PRIMARY KEY,
  operator_session_id TEXT REFERENCES operator_sessions(operator_session_id),
  screen_id TEXT NOT NULL,
  transaction_code TEXT,
  customer_id TEXT,
  account_id TEXT,
  reason TEXT,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE masking_access_logs (
  masking_access_log_id TEXT PRIMARY KEY,
  operator_session_id TEXT REFERENCES operator_sessions(operator_session_id),
  screen_id TEXT NOT NULL,
  customer_id TEXT,
  account_id TEXT,
  masking_policy TEXT NOT NULL,
  access_level TEXT NOT NULL CHECK (access_level IN ('MASKED', 'UNMASK_REQUESTED', 'UNMASK_APPROVED', 'UNMASK_DENIED')),
  reason TEXT NOT NULL,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION reject_ledger_source_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'finalized ledger source rows are append-only; use reversal or adjustment';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_no_update_delete
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW
WHEN (OLD.status IN ('POSTED', 'REVERSED'))
EXECUTE FUNCTION reject_ledger_source_mutation();

CREATE TRIGGER ledger_postings_no_update_delete
BEFORE UPDATE OR DELETE ON ledger_postings
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_source_mutation();

CREATE INDEX idx_ledger_postings_transaction ON ledger_postings(ledger_transaction_id);
CREATE INDEX idx_ledger_postings_account ON ledger_postings(account_id, currency);
CREATE INDEX idx_audit_events_actor_created ON audit_events(actor_id, created_at);
CREATE INDEX idx_audit_events_customer_created ON audit_events(customer_id, created_at);
CREATE INDEX idx_operator_approvals_status ON operator_approvals(status, requested_at);
