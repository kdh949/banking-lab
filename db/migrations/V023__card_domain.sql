-- Synthetic card domain slice. PAN is represented only by token plus last4 metadata.

ALTER TABLE ledger_postings
  DROP CONSTRAINT ledger_postings_posting_type_check;

ALTER TABLE ledger_postings
  ADD CONSTRAINT ledger_postings_posting_type_check
  CHECK (
    posting_type IN (
      'PRINCIPAL',
      'FEE',
      'TAX',
      'HOLD',
      'REVERSAL',
      'OPENING',
      'ADJUSTMENT',
      'INTEREST',
      'LOAN_PRINCIPAL',
      'LOAN_INTEREST',
      'LOAN_REPAYMENT',
      'CARD_PURCHASE'
    )
  );

CREATE TABLE cards (
  card_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  pan_token TEXT NOT NULL UNIQUE,
  pan_last4 CHAR(4) NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'LOST', 'CLOSED')),
  issued_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT cards_pan_token_not_raw CHECK (pan_token !~ '^[0-9]{12,19}$')
);

CREATE INDEX idx_cards_customer ON cards(customer_id, status);
CREATE INDEX idx_cards_account ON cards(account_id, status);

CREATE TABLE card_limits (
  card_id TEXT PRIMARY KEY REFERENCES cards(card_id),
  daily_limit_minor BIGINT NOT NULL CHECK (daily_limit_minor >= 0),
  monthly_limit_minor BIGINT NOT NULL CHECK (monthly_limit_minor >= 0),
  single_limit_minor BIGINT NOT NULL CHECK (single_limit_minor >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE card_limit_usage_counters (
  card_id TEXT NOT NULL REFERENCES cards(card_id),
  period_kind TEXT NOT NULL CHECK (period_kind IN ('DAILY', 'MONTHLY')),
  business_date DATE NOT NULL,
  used_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (used_amount_minor >= 0),
  version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (card_id, period_kind, business_date)
);

CREATE TABLE card_3ds_simulations (
  authentication_id TEXT PRIMARY KEY,
  card_id TEXT NOT NULL REFERENCES cards(card_id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  status TEXT NOT NULL CHECK (status IN ('AUTHENTICATED', 'FAILED')),
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE card_authorizations (
  authorization_id TEXT PRIMARY KEY,
  card_id TEXT NOT NULL REFERENCES cards(card_id),
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  merchant_name TEXT NOT NULL,
  business_date DATE NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('HELD', 'CANCELLED', 'CAPTURED', 'DECLINED')),
  hold_id TEXT REFERENCES account_holds(hold_id),
  three_ds_authentication_id TEXT REFERENCES card_3ds_simulations(authentication_id),
  requested_by TEXT NOT NULL,
  requested_channel TEXT NOT NULL,
  reason TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_card_authorizations_card ON card_authorizations(card_id, status, business_date);

CREATE TABLE card_captures (
  capture_id TEXT PRIMARY KEY,
  authorization_id TEXT NOT NULL REFERENCES card_authorizations(authorization_id),
  card_id TEXT NOT NULL REFERENCES cards(card_id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  status TEXT NOT NULL CHECK (status IN ('POSTED', 'REVERSED')),
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reversed_at TIMESTAMPTZ
);

CREATE INDEX idx_card_captures_authorization ON card_captures(authorization_id, status);
