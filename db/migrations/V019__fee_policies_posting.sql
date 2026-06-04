-- Fee policy versioning and fee posting state for the synthetic lab.
-- Fee charges are represented by append-only balanced ledger postings.
-- Approved fee waivers can reference a reversal transaction when refunding a posted fee.

CREATE TABLE fee_policies (
  policy_id TEXT PRIMARY KEY,
  fee_code TEXT NOT NULL UNIQUE,
  fee_name TEXT NOT NULL,
  product_id TEXT REFERENCES deposit_products(product_id),
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
  waiver_eligible BOOLEAN NOT NULL DEFAULT TRUE,
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fee_policies_product_status
  ON fee_policies(product_id, status);

CREATE TABLE fee_policy_versions (
  fee_policy_version_id TEXT PRIMARY KEY,
  policy_id TEXT NOT NULL REFERENCES fee_policies(policy_id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
  effective_from DATE NOT NULL,
  effective_to DATE,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at TIMESTAMPTZ,
  CONSTRAINT fee_policy_version_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_fee_policy_versions_policy
  ON fee_policy_versions(policy_id, effective_from DESC);

CREATE TABLE fee_policy_change_requests (
  request_id TEXT PRIMARY KEY,
  policy_id TEXT NOT NULL REFERENCES fee_policies(policy_id),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  requested_amount_minor BIGINT NOT NULL CHECK (requested_amount_minor >= 0),
  effective_from DATE NOT NULL,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED')),
  idempotency_key TEXT NOT NULL,
  applied_fee_policy_version_id TEXT REFERENCES fee_policy_versions(fee_policy_version_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  applied_at TIMESTAMPTZ,
  UNIQUE (requested_by, idempotency_key)
);

CREATE INDEX idx_fee_policy_change_requests_policy_status
  ON fee_policy_change_requests(policy_id, status);

CREATE TABLE fee_posting_batches (
  batch_id TEXT PRIMARY KEY,
  policy_id TEXT NOT NULL REFERENCES fee_policies(policy_id),
  fee_policy_version_id TEXT NOT NULL REFERENCES fee_policy_versions(fee_policy_version_id),
  business_date DATE NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('POSTED')),
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  total_fee_minor BIGINT NOT NULL CHECK (total_fee_minor > 0),
  account_count INTEGER NOT NULL CHECK (account_count > 0),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE fee_waiver_requests
  ADD COLUMN refund_ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id);
