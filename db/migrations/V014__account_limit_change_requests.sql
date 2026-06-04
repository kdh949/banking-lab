-- API-backed staff transfer limit change requests.
-- Limit changes are operational account policy changes; ledger source rows remain immutable.

CREATE TABLE account_limit_change_requests (
  request_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL CHECK (business_type = 'TRANSFER_LIMIT_CHANGE'),
  business_reference_id TEXT NOT NULL UNIQUE,
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  target_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  target_transaction_id TEXT,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  current_daily_transfer_limit_minor BIGINT NOT NULL CHECK (current_daily_transfer_limit_minor >= 0),
  current_single_transfer_limit_minor BIGINT NOT NULL CHECK (current_single_transfer_limit_minor >= 0),
  requested_daily_transfer_limit_minor BIGINT NOT NULL CHECK (requested_daily_transfer_limit_minor >= 0),
  requested_single_transfer_limit_minor BIGINT NOT NULL CHECK (requested_single_transfer_limit_minor >= 0),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPLIED')),
  approval_id TEXT UNIQUE REFERENCES operator_approvals(approval_id),
  idempotency_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CHECK (current_single_transfer_limit_minor <= current_daily_transfer_limit_minor),
  CHECK (requested_single_transfer_limit_minor <= requested_daily_transfer_limit_minor),
  UNIQUE (business_type, requested_by, idempotency_key)
);

CREATE INDEX idx_account_limit_change_requests_account_status
  ON account_limit_change_requests(target_account_id, status, created_at);

CREATE INDEX idx_account_limit_change_requests_approval
  ON account_limit_change_requests(approval_id);
