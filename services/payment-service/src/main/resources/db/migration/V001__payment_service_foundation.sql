CREATE TABLE payment_billers (
  biller_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  category TEXT NOT NULL,
  network_kind TEXT NOT NULL CHECK (network_kind = 'SYNTHETIC_BILLER_SIMULATOR'),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_instructions (
  payment_instruction_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL,
  debit_account_id TEXT NOT NULL,
  biller_id TEXT NOT NULL REFERENCES payment_billers(biller_id),
  biller_name TEXT NOT NULL,
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency TEXT NOT NULL DEFAULT 'KRW',
  status TEXT NOT NULL CHECK (status IN ('POSTING_REQUESTED', 'SETTLED', 'CANCELED', 'FAILED')),
  ledger_transaction_id TEXT,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_attempts (
  payment_attempt_id TEXT PRIMARY KEY,
  payment_instruction_id TEXT NOT NULL REFERENCES payment_instructions(payment_instruction_id),
  attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
  status TEXT NOT NULL CHECK (status IN ('POSTING_REQUESTED', 'SETTLED', 'CANCELED', 'FAILED')),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  CONSTRAINT payment_attempt_once UNIQUE (payment_instruction_id, attempt_no)
);

CREATE TABLE payment_status_history (
  payment_status_history_id TEXT PRIMARY KEY,
  payment_instruction_id TEXT NOT NULL REFERENCES payment_instructions(payment_instruction_id),
  status TEXT NOT NULL CHECK (status IN ('POSTING_REQUESTED', 'SETTLED', 'CANCELED', 'FAILED')),
  actor_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_idempotency_keys (
  idempotency_key TEXT PRIMARY KEY,
  command_type TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  response_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_outbox_events (
  outbox_event_id TEXT PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  payload_json JSONB NOT NULL,
  headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER')),
  retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  next_retry_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  error_message TEXT,
  CONSTRAINT payment_outbox_event_once UNIQUE (aggregate_id, event_type, idempotency_key)
);

CREATE INDEX idx_payment_instructions_customer ON payment_instructions(customer_id, created_at DESC);
CREATE INDEX idx_payment_instructions_status ON payment_instructions(status, updated_at DESC);
CREATE INDEX idx_payment_outbox_events_status_retry ON payment_outbox_events(status, next_retry_at, created_at);

INSERT INTO payment_billers (biller_id, display_name, category, network_kind, synthetic_only)
VALUES
  ('SYN-BILLER-UTIL-001', 'Synthetic Utility Biller', 'UTILITY', 'SYNTHETIC_BILLER_SIMULATOR', true),
  ('SYN-BILLER-TELCO-001', 'Synthetic Telco Biller', 'TELCO', 'SYNTHETIC_BILLER_SIMULATOR', true),
  ('SYN-BILLER-TAX-001', 'Synthetic Local Tax Biller', 'PUBLIC', 'SYNTHETIC_BILLER_SIMULATOR', true);
