-- API-backed staff account hold and release requests.
-- Ledger source rows remain immutable; account hold effects are operational state and projections only.

CREATE TABLE account_hold_requests (
  request_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL CHECK (business_type IN ('ACCOUNT_HOLD', 'ACCOUNT_HOLD_RELEASE')),
  business_reference_id TEXT NOT NULL UNIQUE,
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  target_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  target_transaction_id TEXT,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  hold_amount_minor BIGINT NOT NULL CHECK (hold_amount_minor >= 0),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'RELEASED')),
  approval_id TEXT UNIQUE REFERENCES operator_approvals(approval_id),
  idempotency_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (business_type, requested_by, idempotency_key)
);

CREATE INDEX idx_account_hold_requests_account_status
  ON account_hold_requests(target_account_id, status, created_at);

CREATE INDEX idx_account_hold_requests_approval
  ON account_hold_requests(approval_id);
