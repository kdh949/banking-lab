-- Synthetic account opening request workflow.
-- Generated accounts are synthetic-only; optional opening deposits must post
-- through LedgerCommandService and balanced ledger postings, never balance edits.

CREATE SEQUENCE IF NOT EXISTS synthetic_account_opening_seq
  START WITH 100001
  INCREMENT BY 1;

CREATE TABLE account_opening_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED')),
  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  approval_id TEXT NOT NULL UNIQUE REFERENCES operator_approvals(approval_id),
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  requested_product_code TEXT NOT NULL,
  requested_account_alias TEXT,
  requested_currency CHAR(3) NOT NULL DEFAULT 'KRW',
  requested_daily_transfer_limit_minor BIGINT NOT NULL CHECK (requested_daily_transfer_limit_minor >= 0),
  requested_single_transfer_limit_minor BIGINT NOT NULL CHECK (requested_single_transfer_limit_minor >= 0),
  requested_initial_deposit_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (requested_initial_deposit_amount_minor >= 0),
  requested_initial_deposit_idempotency_key TEXT,
  requested_business_date DATE,
  generated_account_id TEXT UNIQUE REFERENCES accounts(account_id),
  generated_account_no TEXT UNIQUE,
  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,
  executed_by TEXT,
  executed_by_role TEXT,
  execute_reason TEXT,
  execute_idempotency_key TEXT UNIQUE,
  execute_command_hash TEXT,
  initial_deposit_ledger_transaction_id TEXT UNIQUE REFERENCES ledger_transactions(ledger_transaction_id),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{"syntheticOnly":true,"realPaymentNetworkCalled":false}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT account_opening_initial_deposit_key_required CHECK (
    requested_initial_deposit_amount_minor = 0
    OR requested_initial_deposit_idempotency_key IS NOT NULL
  ),
  CONSTRAINT account_opening_limits_consistent CHECK (
    requested_single_transfer_limit_minor <= requested_daily_transfer_limit_minor
  ),
  CONSTRAINT account_opening_synthetic_only CHECK (synthetic_only = true)
);

CREATE INDEX idx_account_opening_requests_status
  ON account_opening_requests(status, created_at);

CREATE INDEX idx_account_opening_requests_customer
  ON account_opening_requests(target_customer_id, status);
