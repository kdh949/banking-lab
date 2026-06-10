-- Synthetic call-center workflow tables.
-- Notes are stored only after PII-like pattern redaction; audit payloads must not
-- copy free-form note bodies.

CREATE TABLE call_center_interactions (
  interaction_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  account_id TEXT REFERENCES accounts(account_id),
  channel TEXT NOT NULL CHECK (channel IN ('PHONE', 'CHAT', 'EMAIL', 'BRANCH', 'WEB')),
  contact_reason_code TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'AFTERCALL', 'ESCALATED', 'CLOSED')),
  created_by TEXT NOT NULL,
  created_by_role TEXT NOT NULL,
  assigned_to TEXT,
  reason TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  CONSTRAINT call_center_interactions_synthetic_only CHECK (synthetic_only = TRUE),
  CONSTRAINT call_center_interactions_closed_has_end CHECK (status <> 'CLOSED' OR ended_at IS NOT NULL)
);

CREATE TABLE call_center_notes (
  note_id TEXT PRIMARY KEY,
  interaction_id TEXT NOT NULL REFERENCES call_center_interactions(interaction_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  created_by TEXT NOT NULL,
  created_by_role TEXT NOT NULL,
  note_body_redacted TEXT NOT NULL,
  redaction_applied BOOLEAN NOT NULL,
  pii_pattern_count INTEGER NOT NULL CHECK (pii_pattern_count >= 0),
  reason TEXT NOT NULL,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT call_center_notes_synthetic_only CHECK (synthetic_only = TRUE)
);

CREATE TABLE call_center_aftercall_tasks (
  task_id TEXT PRIMARY KEY,
  interaction_id TEXT NOT NULL REFERENCES call_center_interactions(interaction_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  task_type TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
  assigned_to TEXT,
  due_at TIMESTAMPTZ,
  created_by TEXT NOT NULL,
  created_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT call_center_aftercall_tasks_synthetic_only CHECK (synthetic_only = TRUE)
);

CREATE TABLE call_center_escalations (
  escalation_id TEXT PRIMARY KEY,
  interaction_id TEXT NOT NULL REFERENCES call_center_interactions(interaction_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  escalation_type TEXT NOT NULL CHECK (escalation_type IN ('MANAGER', 'COMPLAINT', 'FDS', 'AML')),
  status TEXT NOT NULL CHECK (status IN ('CREATED', 'ACCEPTED', 'REJECTED', 'CLOSED')),
  complaint_case_id TEXT REFERENCES complaint_cases(complaint_case_id),
  requested_by TEXT NOT NULL,
  requested_by_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT call_center_escalations_synthetic_only CHECK (synthetic_only = TRUE)
);

CREATE TABLE call_center_access_audit (
  call_center_access_audit_id TEXT PRIMARY KEY,
  interaction_id TEXT REFERENCES call_center_interactions(interaction_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  action_type TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  screen_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  audit_event_id TEXT NOT NULL REFERENCES audit_events(audit_event_id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT call_center_access_audit_synthetic_only CHECK (synthetic_only = TRUE)
);

CREATE INDEX idx_call_center_interactions_customer_created
  ON call_center_interactions(customer_id, started_at DESC);

CREATE INDEX idx_call_center_interactions_status_assignee
  ON call_center_interactions(status, assigned_to, started_at DESC);

CREATE INDEX idx_call_center_notes_interaction_created
  ON call_center_notes(interaction_id, created_at);

CREATE INDEX idx_call_center_aftercall_tasks_status_due
  ON call_center_aftercall_tasks(status, due_at);

CREATE INDEX idx_call_center_escalations_interaction_created
  ON call_center_escalations(interaction_id, created_at);

