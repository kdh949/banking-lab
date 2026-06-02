-- Additive workflow case lifecycle tables for Agent B Kotlin parity.
-- Existing Node runtime remains the behavior oracle; these tables prepare Spring persistence.

CREATE TABLE complaint_cases (
  complaint_case_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  status TEXT NOT NULL CHECK (
    status IN (
      'RECEIVED', 'CLASSIFIED', 'ASSIGNED', 'IN_REVIEW', 'WAITING_CUSTOMER',
      'WAITING_APPROVAL', 'ANSWERED', 'CLOSED', 'REOPENED', 'TRANSFERRED_TO_AUTHORITY_SIM'
    )
  ),
  sla_due_at TIMESTAMPTZ NOT NULL,
  classification TEXT,
  owner_id TEXT,
  answer_json JSONB,
  answer_draft_json JSONB,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  customer_confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT complaint_answer_requires_answered CHECK (answer_json IS NULL OR status IN ('ANSWERED', 'CLOSED', 'REOPENED')),
  CONSTRAINT complaint_draft_requires_approval CHECK (answer_draft_json IS NULL OR status = 'WAITING_APPROVAL')
);

CREATE TABLE complaint_case_timeline (
  complaint_timeline_id TEXT PRIMARY KEY,
  complaint_case_id TEXT NOT NULL REFERENCES complaint_cases(complaint_case_id),
  event_type TEXT NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  actor_id TEXT,
  note TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fds_case_timeline (
  fds_timeline_id TEXT PRIMARY KEY,
  fds_case_id TEXT NOT NULL REFERENCES fds_cases(fds_case_id),
  event_type TEXT NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  actor_id TEXT,
  note TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aml_case_comments (
  aml_comment_id TEXT PRIMARY KEY,
  aml_case_id TEXT NOT NULL REFERENCES aml_cases(aml_case_id),
  actor_id TEXT NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_adjustment_requests (
  reconciliation_adjustment_request_id TEXT PRIMARY KEY,
  reconciliation_item_id TEXT NOT NULL REFERENCES reconciliation_items(reconciliation_item_id),
  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  direction TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  business_date DATE NOT NULL,
  idempotency_key TEXT NOT NULL,
  reason TEXT NOT NULL,
  requested_by TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED', 'POSTED', 'REJECTED')),
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT reconciliation_adjustment_once UNIQUE (reconciliation_item_id, idempotency_key)
);

CREATE INDEX idx_complaint_cases_customer_status ON complaint_cases(customer_id, status, created_at);
CREATE INDEX idx_complaint_cases_status_owner ON complaint_cases(status, owner_id, created_at);
CREATE INDEX idx_complaint_timeline_case_created ON complaint_case_timeline(complaint_case_id, created_at);
CREATE INDEX idx_fds_timeline_case_created ON fds_case_timeline(fds_case_id, created_at);
CREATE INDEX idx_aml_comments_case_created ON aml_case_comments(aml_case_id, created_at);
CREATE INDEX idx_reconciliation_adjustment_status ON reconciliation_adjustment_requests(status, created_at);
