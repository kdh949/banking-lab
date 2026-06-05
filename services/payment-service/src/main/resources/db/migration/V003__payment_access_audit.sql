CREATE TABLE payment_access_audit_events (
  audit_event_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL CHECK (event_type = 'PAYMENT_INSTRUCTION_VIEW'),
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  actor_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
  screen_id TEXT NOT NULL,
  payment_instruction_id TEXT NOT NULL REFERENCES payment_instructions(payment_instruction_id) ON DELETE CASCADE,
  customer_id TEXT NOT NULL,
  debit_account_id TEXT NOT NULL,
  reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
  pii_access BOOLEAN NOT NULL DEFAULT true,
  masking_policy TEXT NOT NULL DEFAULT 'ACCOUNT_PII',
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_access_audit_instruction ON payment_access_audit_events(payment_instruction_id, created_at DESC);
CREATE INDEX idx_payment_access_audit_actor ON payment_access_audit_events(actor_id, created_at DESC);
