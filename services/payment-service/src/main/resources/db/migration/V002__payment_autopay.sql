CREATE TABLE payment_autopay_agreements (
  autopay_agreement_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL,
  debit_account_id TEXT NOT NULL,
  biller_id TEXT NOT NULL REFERENCES payment_billers(biller_id),
  biller_name TEXT NOT NULL,
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency TEXT NOT NULL DEFAULT 'KRW',
  frequency TEXT NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELED')),
  next_run_on DATE NOT NULL,
  last_run_on DATE,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_by TEXT NOT NULL,
  created_reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_autopay_status_history (
  autopay_status_history_id TEXT PRIMARY KEY,
  autopay_agreement_id TEXT NOT NULL REFERENCES payment_autopay_agreements(autopay_agreement_id),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELED')),
  actor_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_autopay_executions (
  autopay_execution_id TEXT PRIMARY KEY,
  autopay_agreement_id TEXT NOT NULL REFERENCES payment_autopay_agreements(autopay_agreement_id),
  scheduled_run_on DATE NOT NULL,
  payment_instruction_id TEXT NOT NULL REFERENCES payment_instructions(payment_instruction_id),
  status TEXT NOT NULL CHECK (status = 'INSTRUCTION_CREATED'),
  idempotency_key TEXT NOT NULL UNIQUE,
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payment_autopay_execution_once UNIQUE (autopay_agreement_id, scheduled_run_on)
);

CREATE INDEX idx_payment_autopay_customer ON payment_autopay_agreements(customer_id, status, next_run_on);
CREATE INDEX idx_payment_autopay_due ON payment_autopay_agreements(status, next_run_on);
CREATE INDEX idx_payment_autopay_executions_agreement ON payment_autopay_executions(autopay_agreement_id, scheduled_run_on DESC);
