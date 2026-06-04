-- End-of-day closing step read model.
-- Steps are operational projections; ledger source rows stay append-only.

CREATE TABLE eod_closing_steps (
  business_date DATE NOT NULL,
  step TEXT NOT NULL CHECK (
    step IN (
      'INTEREST_ACCRUAL',
      'INTEREST_POSTING',
      'FEE_POSTING',
      'RECONCILIATION',
      'DAILY_CLOSING'
    )
  ),
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'SKIPPED', 'FAILED')),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  version BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY (business_date, step)
);

CREATE INDEX idx_eod_closing_steps_status
  ON eod_closing_steps(status, business_date);
