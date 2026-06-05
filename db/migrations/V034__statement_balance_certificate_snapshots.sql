CREATE TABLE balance_certificate_snapshots (
  certificate_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  currency CHAR(3) NOT NULL,
  as_of_date DATE NOT NULL,
  balance_as_of_minor BIGINT NOT NULL,
  current_ledger_balance_minor BIGINT NOT NULL,
  current_available_balance_minor BIGINT NOT NULL,
  deterministic_input_hash TEXT NOT NULL,
  source_posting_count INTEGER NOT NULL CHECK (source_posting_count >= 0),
  source_last_business_date DATE,
  source_ledger_hash TEXT NOT NULL,
  first_audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  last_audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT balance_certificate_snapshot_unique_source UNIQUE (account_id, as_of_date, deterministic_input_hash)
);

CREATE INDEX idx_balance_certificate_snapshots_account_date
  ON balance_certificate_snapshots(account_id, as_of_date DESC);
