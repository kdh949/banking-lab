-- Staff access, audit hash-chain, maker-checker approval, and workflow references.

CREATE TABLE audit_events (
  audit_event_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  actor_type TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  branch_id TEXT,
  screen_id TEXT,
  business_reference_id TEXT,
  customer_id TEXT,
  account_id TEXT,
  reason TEXT,
  ip_address INET,
  user_agent TEXT,
  device_id TEXT,
  before_hash TEXT,
  payload_hash TEXT NOT NULL,
  previous_event_hash TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE operator_approvals (
  approval_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  requested_by TEXT NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  request_reason TEXT NOT NULL,
  before_snapshot_json JSONB,
  after_snapshot_json JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  approved_by TEXT,
  approved_at TIMESTAMPTZ,
  rejected_by TEXT,
  rejected_at TIMESTAMPTZ,
  reject_reason TEXT,
  audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  CONSTRAINT maker_checker_different CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

CREATE TABLE operator_sessions (
  operator_session_id TEXT PRIMARY KEY,
  operator_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  branch_id TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ
);

CREATE TABLE screen_access_logs (
  screen_access_log_id TEXT PRIMARY KEY,
  operator_session_id TEXT REFERENCES operator_sessions(operator_session_id),
  screen_id TEXT NOT NULL,
  transaction_code TEXT,
  customer_id TEXT,
  account_id TEXT,
  reason TEXT,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE masking_access_logs (
  masking_access_log_id TEXT PRIMARY KEY,
  operator_session_id TEXT REFERENCES operator_sessions(operator_session_id),
  screen_id TEXT NOT NULL,
  customer_id TEXT,
  account_id TEXT,
  masking_policy TEXT NOT NULL,
  access_level TEXT NOT NULL CHECK (access_level IN ('MASKED', 'UNMASK_REQUESTED', 'UNMASK_APPROVED', 'UNMASK_DENIED')),
  reason TEXT NOT NULL,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workflow_instances (
  workflow_instance_id TEXT PRIMARY KEY,
  workflow_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  temporal_workflow_id TEXT,
  temporal_run_id TEXT,
  status TEXT NOT NULL CHECK (status IN ('STARTED', 'RUNNING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED')),
  started_by TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workflow_events (
  workflow_event_id TEXT PRIMARY KEY,
  workflow_instance_id TEXT NOT NULL REFERENCES workflow_instances(workflow_instance_id),
  event_type TEXT NOT NULL,
  actor_id TEXT,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_actor_created ON audit_events(actor_id, created_at);
CREATE INDEX idx_audit_events_customer_created ON audit_events(customer_id, created_at);
CREATE INDEX idx_operator_approvals_status ON operator_approvals(status, requested_at);
CREATE INDEX idx_workflow_instances_reference ON workflow_instances(business_reference_id, workflow_type);
