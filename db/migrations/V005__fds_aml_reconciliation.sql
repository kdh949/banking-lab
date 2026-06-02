-- FDS, AML, and reconciliation control tables for later workflow phases.

CREATE TABLE reconciliation_items (
  reconciliation_item_id TEXT PRIMARY KEY,
  business_date DATE NOT NULL,
  source_system TEXT NOT NULL,
  internal_reference_id TEXT,
  external_reference_id TEXT,
  amount_minor BIGINT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (
    status IN ('OPEN', 'INVESTIGATING', 'MATCHED', 'ADJUSTMENT_REQUESTED', 'ADJUSTED', 'WAIVED', 'CLOSED')
  ),
  owner_id TEXT,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT open_item_requires_owner CHECK (status <> 'INVESTIGATING' OR owner_id IS NOT NULL)
);

CREATE TABLE fds_cases (
  fds_case_id TEXT PRIMARY KEY,
  transfer_reference_id TEXT NOT NULL,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  status TEXT NOT NULL CHECK (status IN ('HELD', 'INVESTIGATING', 'RELEASE_REQUESTED', 'RELEASED', 'BLOCK_REQUESTED', 'BLOCKED')),
  risk_score INTEGER NOT NULL CHECK (risk_score >= 0 AND risk_score <= 1000),
  alerts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  owner_id TEXT,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aml_cases (
  aml_case_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'INVESTIGATING', 'CLOSURE_REQUESTED', 'CLOSED')),
  risk_score INTEGER NOT NULL CHECK (risk_score >= 0 AND risk_score <= 1000),
  alerts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  owner_id TEXT,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  str_simulation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reconciliation_items_business_date ON reconciliation_items(business_date, status);
CREATE INDEX idx_fds_cases_status ON fds_cases(status, created_at);
CREATE INDEX idx_aml_cases_status ON aml_cases(status, created_at);
