-- Deposit product, rate versioning, and interest posting state for the synthetic lab.
-- Interest payout is represented by append-only balanced ledger postings.

ALTER TABLE ledger_postings
  DROP CONSTRAINT ledger_postings_posting_type_check;

ALTER TABLE ledger_postings
  ADD CONSTRAINT ledger_postings_posting_type_check
  CHECK (posting_type IN ('PRINCIPAL', 'FEE', 'TAX', 'HOLD', 'REVERSAL', 'OPENING', 'ADJUSTMENT', 'INTEREST'));

CREATE TABLE deposit_products (
  product_id TEXT PRIMARY KEY,
  product_code TEXT NOT NULL UNIQUE,
  product_name TEXT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
  minimum_opening_balance_minor BIGINT NOT NULL DEFAULT 0 CHECK (minimum_opening_balance_minor >= 0),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_interest_rate_versions (
  rate_version_id TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES deposit_products(product_id),
  annual_rate_bps INTEGER NOT NULL CHECK (annual_rate_bps >= 0 AND annual_rate_bps <= 20000),
  effective_from DATE NOT NULL,
  effective_to DATE,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at TIMESTAMPTZ,
  CONSTRAINT product_interest_rate_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_product_interest_rate_versions_product ON product_interest_rate_versions(product_id, effective_from DESC);

CREATE TABLE account_product_enrollments (
  enrollment_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  product_id TEXT NOT NULL REFERENCES deposit_products(product_id),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
  enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  UNIQUE (account_id, product_id)
);

CREATE INDEX idx_account_product_enrollments_account ON account_product_enrollments(account_id, status);

CREATE TABLE interest_accruals (
  accrual_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  product_id TEXT NOT NULL REFERENCES deposit_products(product_id),
  rate_version_id TEXT NOT NULL REFERENCES product_interest_rate_versions(rate_version_id),
  accrual_date DATE NOT NULL,
  balance_minor BIGINT NOT NULL CHECK (balance_minor >= 0),
  annual_rate_bps INTEGER NOT NULL CHECK (annual_rate_bps >= 0),
  accrued_interest_minor BIGINT NOT NULL CHECK (accrued_interest_minor >= 0),
  status TEXT NOT NULL CHECK (status IN ('CALCULATED', 'POSTED')),
  batch_id TEXT,
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at TIMESTAMPTZ,
  UNIQUE (account_id, accrual_date)
);

CREATE INDEX idx_interest_accruals_status_date ON interest_accruals(status, accrual_date);

CREATE TABLE interest_posting_batches (
  batch_id TEXT PRIMARY KEY,
  business_date DATE NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('POSTED')),
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  total_interest_minor BIGINT NOT NULL CHECK (total_interest_minor > 0),
  account_count INTEGER NOT NULL CHECK (account_count > 0),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE interest_accruals
  ADD CONSTRAINT fk_interest_accrual_batch
  FOREIGN KEY (batch_id) REFERENCES interest_posting_batches(batch_id);

CREATE TABLE deposit_rate_change_requests (
  request_id TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES deposit_products(product_id),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  requested_annual_rate_bps INTEGER NOT NULL CHECK (requested_annual_rate_bps >= 0 AND requested_annual_rate_bps <= 20000),
  effective_from DATE NOT NULL,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED')),
  idempotency_key TEXT NOT NULL,
  applied_rate_version_id TEXT REFERENCES product_interest_rate_versions(rate_version_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  applied_at TIMESTAMPTZ,
  UNIQUE (requested_by, idempotency_key)
);

CREATE INDEX idx_deposit_rate_change_requests_product_status ON deposit_rate_change_requests(product_id, status);
