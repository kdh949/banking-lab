-- Staff transaction correction workflow.
-- Corrections never update finalized ledger rows; approved REVERSAL requests append
-- a new balanced REVERSAL ledger transaction that references the original.

CREATE TABLE transaction_correction_requests (
  request_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL CHECK (business_type = 'TRANSACTION_CORRECTION'),
  business_reference_id TEXT NOT NULL UNIQUE,
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  target_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  target_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  correction_type TEXT NOT NULL CHECK (correction_type IN ('REVERSAL', 'ADJUSTMENT')),
  correction_business_date DATE NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'REVERSED', 'ADJUSTED', 'REJECTED')),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  idempotency_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (business_type, requested_by, idempotency_key)
);

CREATE INDEX idx_transaction_correction_target
  ON transaction_correction_requests(target_transaction_id, status);

CREATE INDEX idx_transaction_correction_status
  ON transaction_correction_requests(status, created_at);
