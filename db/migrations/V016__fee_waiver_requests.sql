-- API-backed staff fee waiver requests.
-- Fee waiver approval records an operational waiver decision only; it does not create
-- ledger postings or mutate ledger source rows. Fee refunds remain correction work.

CREATE TABLE fee_waiver_requests (
  request_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL CHECK (business_type = 'FEE_WAIVER'),
  business_reference_id TEXT NOT NULL UNIQUE,
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  target_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  target_transaction_id TEXT,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  fee_code TEXT NOT NULL,
  waived_amount_minor BIGINT NOT NULL CHECK (waived_amount_minor > 0),
  currency TEXT NOT NULL CHECK (char_length(currency) = 3),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
  approval_id TEXT UNIQUE REFERENCES operator_approvals(approval_id),
  idempotency_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (business_type, requested_by, idempotency_key)
);

CREATE INDEX idx_fee_waiver_requests_account_status
  ON fee_waiver_requests(target_account_id, status, created_at);

CREATE INDEX idx_fee_waiver_requests_approval
  ON fee_waiver_requests(approval_id);
