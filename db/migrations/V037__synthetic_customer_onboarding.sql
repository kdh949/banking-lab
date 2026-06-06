-- Synthetic customer onboarding request workflow.
-- This is a lab-only customer creation path: no real PII, KYC provider, Keycloak Admin API, or external identity system is called.

CREATE SEQUENCE synthetic_customer_onboarding_customer_seq START 1;

CREATE TABLE customer_auth_identities (
  auth_subject TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL UNIQUE REFERENCES customers(customer_id),
  username TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
  failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
  last_login_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{"syntheticOnly":true}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_customer_auth_identities_username_lower
  ON customer_auth_identities ((lower(username)));

CREATE TABLE customer_onboarding_requests (
  request_id TEXT PRIMARY KEY,
  idempotency_key TEXT NOT NULL UNIQUE,
  command_hash TEXT NOT NULL,

  status TEXT NOT NULL CHECK (status IN (
    'PENDING_APPROVAL',
    'APPROVED',
    'REJECTED',
    'EXECUTED',
    'FAILED'
  )),

  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,

  approval_id TEXT NOT NULL UNIQUE REFERENCES operator_approvals(approval_id),

  requested_customer_name TEXT NOT NULL,
  requested_customer_phone TEXT NOT NULL,
  requested_customer_address TEXT NOT NULL,
  requested_customer_grade TEXT NOT NULL,
  requested_risk_grade TEXT NOT NULL,
  requested_source_of_funds_code TEXT NOT NULL,
  requested_transaction_purpose_code TEXT NOT NULL,
  requested_username TEXT NOT NULL,
  password_policy TEXT NOT NULL DEFAULT 'SYNTHETIC_MIN_8_NOT_USERNAME',
  requested_password_hash TEXT NOT NULL,

  generated_customer_id TEXT UNIQUE REFERENCES customers(customer_id),
  generated_auth_subject TEXT UNIQUE,

  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,

  executed_by TEXT,
  executed_by_role TEXT,
  execute_reason TEXT,
  execute_idempotency_key TEXT UNIQUE,
  execute_command_hash TEXT,
  executed_at TIMESTAMPTZ,

  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_customer_onboarding_active_username_lower
  ON customer_onboarding_requests ((lower(requested_username)))
  WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTED');

CREATE INDEX idx_customer_onboarding_status_created
  ON customer_onboarding_requests(status, created_at DESC);

CREATE INDEX idx_customer_onboarding_approval
  ON customer_onboarding_requests(approval_id);
