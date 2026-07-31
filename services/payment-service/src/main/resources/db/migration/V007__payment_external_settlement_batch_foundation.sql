CREATE TABLE payment_external_settlement_imports (
  external_settlement_import_id TEXT PRIMARY KEY,
  original_file_name TEXT NOT NULL CHECK (length(original_file_name) BETWEEN 1 AND 255),
  institution_code TEXT NOT NULL CHECK (institution_code ~ '^[A-Z0-9][A-Z0-9_-]{2,39}$'),
  file_sha256 TEXT NOT NULL UNIQUE CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
  file_byte_size BIGINT NOT NULL CHECK (file_byte_size BETWEEN 1 AND 1000000),
  business_date DATE NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('IMPORTED', 'BATCHED')),
  line_count INTEGER NOT NULL CHECK (line_count BETWEEN 1 AND 10000),
  accepted_line_count INTEGER NOT NULL CHECK (accepted_line_count >= 0),
  rejected_line_count INTEGER NOT NULL CHECK (rejected_line_count >= 0),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payment_external_settlement_import_line_counts
    CHECK (line_count = accepted_line_count + rejected_line_count)
);

CREATE TABLE payment_external_settlement_lines (
  external_settlement_line_id TEXT PRIMARY KEY,
  external_settlement_import_id TEXT NOT NULL
    REFERENCES payment_external_settlement_imports(external_settlement_import_id),
  line_number INTEGER NOT NULL CHECK (line_number >= 2),
  external_reference TEXT NOT NULL,
  payment_instruction_id TEXT NOT NULL CHECK (payment_instruction_id LIKE 'PAY-%'),
  biller_id TEXT NOT NULL CHECK (biller_id LIKE 'SYN-BILLER-%'),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  business_date DATE NOT NULL,
  value_date DATE NOT NULL CHECK (value_date >= business_date),
  status TEXT NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED', 'RETURNED')),
  raw_line TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payment_external_settlement_line_once
    UNIQUE (external_settlement_import_id, line_number)
);

CREATE TABLE payment_settlement_batch_runs (
  settlement_batch_run_id TEXT PRIMARY KEY,
  external_settlement_import_id TEXT NOT NULL UNIQUE
    REFERENCES payment_external_settlement_imports(external_settlement_import_id),
  fee_rate_bps INTEGER NOT NULL CHECK (fee_rate_bps BETWEEN 0 AND 10000),
  vat_rate_bps INTEGER NOT NULL CHECK (vat_rate_bps BETWEEN 0 AND 10000),
  cutoff_at TIMESTAMPTZ NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_settlement_batches (
  settlement_batch_id TEXT PRIMARY KEY,
  settlement_batch_run_id TEXT NOT NULL
    REFERENCES payment_settlement_batch_runs(settlement_batch_run_id),
  external_settlement_import_id TEXT NOT NULL
    REFERENCES payment_external_settlement_imports(external_settlement_import_id),
  biller_id TEXT NOT NULL CHECK (biller_id LIKE 'SYN-BILLER-%'),
  currency TEXT NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  business_date DATE NOT NULL,
  value_date DATE NOT NULL CHECK (value_date >= business_date),
  cutoff_at TIMESTAMPTZ NOT NULL,
  gross_amount_minor BIGINT NOT NULL CHECK (gross_amount_minor > 0),
  fee_rate_bps INTEGER NOT NULL CHECK (fee_rate_bps BETWEEN 0 AND 10000),
  fee_amount_minor BIGINT NOT NULL CHECK (fee_amount_minor >= 0),
  vat_rate_bps INTEGER NOT NULL CHECK (vat_rate_bps BETWEEN 0 AND 10000),
  vat_amount_minor BIGINT NOT NULL CHECK (vat_amount_minor >= 0),
  adjustment_amount_minor BIGINT NOT NULL DEFAULT 0,
  net_amount_minor BIGINT NOT NULL CHECK (net_amount_minor >= 0),
  status TEXT NOT NULL CHECK (status IN ('INCLUDED_IN_BATCH')),
  external_payout_reference TEXT,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT payment_settlement_batch_position_once
    UNIQUE (
      settlement_batch_run_id,
      biller_id,
      currency,
      business_date,
      value_date
    ),
  CONSTRAINT payment_settlement_batch_arithmetic
    CHECK (
      net_amount_minor =
        gross_amount_minor - fee_amount_minor - vat_amount_minor + adjustment_amount_minor
    )
);

CREATE TABLE payment_settlement_batch_items (
  settlement_batch_item_id TEXT PRIMARY KEY,
  settlement_batch_id TEXT NOT NULL
    REFERENCES payment_settlement_batches(settlement_batch_id),
  external_settlement_line_id TEXT NOT NULL UNIQUE
    REFERENCES payment_external_settlement_lines(external_settlement_line_id),
  payment_instruction_id TEXT NOT NULL CHECK (payment_instruction_id LIKE 'PAY-%'),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_external_settlement_imports_business_date
  ON payment_external_settlement_imports(business_date, received_at DESC);

CREATE INDEX idx_payment_external_settlement_lines_import
  ON payment_external_settlement_lines(
    external_settlement_import_id,
    status,
    biller_id,
    currency,
    value_date,
    line_number
  );

CREATE INDEX idx_payment_settlement_batches_value_date
  ON payment_settlement_batches(value_date, status, biller_id, currency);

COMMENT ON TABLE payment_external_settlement_imports IS
  'Independent synthetic external clearing-file provenance. The file hash is not derived from internal ledger data.';

COMMENT ON COLUMN payment_external_settlement_lines.raw_line IS
  'Original CSV staging row retained for audit and later three-way reconciliation.';

COMMENT ON TABLE payment_settlement_batches IS
  'Per-biller, currency, business-date, and value-date positions calculated as gross minus fee minus VAT plus adjustments.';

COMMENT ON COLUMN payment_settlement_batches.status IS
  'INCLUDED_IN_BATCH does not mean payout or external settlement finality.';
