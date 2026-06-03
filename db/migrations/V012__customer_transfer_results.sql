-- Durable customer transfer command-result read model.
-- Ledger postings remain the source of truth for balances; this table preserves
-- channel-visible POSTED/HELD/FAILED/BLOCKED outcomes without unsafe postings.

CREATE TABLE customer_transfer_results (
  result_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  from_account_id TEXT REFERENCES accounts(account_id),
  to_account_id TEXT REFERENCES accounts(account_id),
  amount_minor BIGINT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (status IN ('POSTED', 'HELD', 'FAILED', 'BLOCKED')),
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  fds_case_id TEXT REFERENCES fds_cases(fds_case_id),
  failure_code TEXT,
  message TEXT NOT NULL,
  requested_by TEXT NOT NULL,
  requested_channel TEXT NOT NULL DEFAULT 'CUSTOMER_WEB',
  business_reference_id TEXT,
  business_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT posted_transfer_requires_ledger_transaction
    CHECK (status <> 'POSTED' OR ledger_transaction_id IS NOT NULL),
  CONSTRAINT held_transfer_requires_fds_case
    CHECK (status <> 'HELD' OR fds_case_id IS NOT NULL),
  CONSTRAINT failed_transfer_requires_failure_code
    CHECK (status <> 'FAILED' OR failure_code IS NOT NULL)
);

CREATE INDEX idx_customer_transfer_results_customer_created
  ON customer_transfer_results(customer_id, created_at, result_id);

CREATE INDEX idx_customer_transfer_results_fds_case
  ON customer_transfer_results(fds_case_id)
  WHERE fds_case_id IS NOT NULL;
