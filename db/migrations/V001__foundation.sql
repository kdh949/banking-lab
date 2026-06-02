-- Synthetic Banking Lab foundation schema.
-- Ledger source-of-truth rows live in ledger_transactions and ledger_postings.

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
  business_date DATE NOT NULL DEFAULT CURRENT_DATE,
  status TEXT NOT NULL CHECK (status IN ('REQUESTED', 'POSTED', 'REVERSED', 'REJECTED')),
  requested_by TEXT NOT NULL,
  requested_channel TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at TIMESTAMPTZ,
  original_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  reason TEXT,
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
  posting_type TEXT NOT NULL CHECK (posting_type IN ('PRINCIPAL', 'FEE', 'TAX', 'HOLD', 'REVERSAL', 'OPENING', 'ADJUSTMENT')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_balance_projections (
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
  response_json JSONB,
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

CREATE INDEX idx_ledger_postings_transaction ON ledger_postings(ledger_transaction_id);
CREATE INDEX idx_ledger_postings_account ON ledger_postings(account_id, currency);
CREATE INDEX idx_ledger_transactions_business_date ON ledger_transactions(business_date, status);
CREATE INDEX idx_idempotency_transaction ON idempotency_keys(ledger_transaction_id);
