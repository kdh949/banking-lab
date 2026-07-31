-- Customer self-service onboarding, account-opening intake, and statement artifacts.
-- This remains synthetic-only: no real KYC, real identity provider, real payment
-- network, or customer document delivery integration is called.

CREATE TABLE customer_onboarding_checks (
  check_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  check_type TEXT NOT NULL CHECK (
    check_type IN (
      'DUPLICATE_IDENTITY',
      'KYC_SIMULATION',
      'TERMS_ACCEPTANCE',
      'CONTACT_REACHABILITY'
    )
  ),
  status TEXT NOT NULL CHECK (
    status IN ('PENDING', 'PASSED', 'REVIEW_REQUIRED', 'FAILED')
  ),
  risk_level TEXT NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
  evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  raw_pii_stored BOOLEAN NOT NULL DEFAULT false CHECK (raw_pii_stored = false),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_onboarding_check_once UNIQUE (customer_id, check_type)
);

CREATE INDEX idx_customer_onboarding_checks_customer_type_created
  ON customer_onboarding_checks(customer_id, check_type, created_at DESC);

CREATE TABLE customer_self_service_account_opening_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  status TEXT NOT NULL CHECK (
    status IN (
      'CUSTOMER_SUBMITTED',
      'STAFF_REVIEWING',
      'STAFF_REQUESTED',
      'REJECTED',
      'APPROVED',
      'EXECUTED',
      'CANCELLED'
    )
  ),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  approval_status TEXT,
  staff_account_opening_request_id TEXT UNIQUE REFERENCES account_opening_requests(request_id),
  requested_product_code TEXT NOT NULL,
  requested_account_alias TEXT,
  requested_currency CHAR(3) NOT NULL DEFAULT 'KRW',
  requested_initial_deposit_amount_minor BIGINT NOT NULL DEFAULT 0
    CHECK (requested_initial_deposit_amount_minor >= 0),
  terms_accepted BOOLEAN NOT NULL CHECK (terms_accepted = true),
  generated_account_id TEXT REFERENCES accounts(account_id),
  generated_account_no TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{"syntheticOnly":true}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_self_service_account_opening_customer_status_created
  ON customer_self_service_account_opening_requests(customer_id, status, created_at DESC);

CREATE INDEX idx_customer_self_service_account_opening_staff_request
  ON customer_self_service_account_opening_requests(staff_account_opening_request_id)
  WHERE staff_account_opening_request_id IS NOT NULL;

CREATE TABLE statement_artifact_snapshots (
  statement_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  account_id TEXT REFERENCES accounts(account_id),
  from_date DATE NOT NULL,
  to_date DATE NOT NULL,
  statement_scope TEXT NOT NULL CHECK (statement_scope IN ('CONSOLIDATED', 'ACCOUNT')),
  source_ledger_hash TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  snapshot_json JSONB NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT statement_artifact_date_range CHECK (to_date >= from_date)
);

CREATE INDEX idx_statement_artifact_snapshots_customer_dates
  ON statement_artifact_snapshots(customer_id, from_date DESC, to_date DESC);

CREATE INDEX idx_statement_artifact_snapshots_account_dates
  ON statement_artifact_snapshots(account_id, from_date DESC, to_date DESC)
  WHERE account_id IS NOT NULL;
