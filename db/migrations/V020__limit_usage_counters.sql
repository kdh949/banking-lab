-- Posting-time transfer limit enforcement.
-- Account limits remain operational policy rows; usage counters are transactional projections.

ALTER TABLE account_limits
  ADD COLUMN monthly_transfer_limit_minor BIGINT,
  ADD COLUMN customer_web_daily_transfer_limit_minor BIGINT,
  ADD COLUMN customer_web_monthly_transfer_limit_minor BIGINT,
  ADD COLUMN customer_web_single_transfer_limit_minor BIGINT,
  ADD COLUMN staff_terminal_daily_transfer_limit_minor BIGINT,
  ADD COLUMN staff_terminal_monthly_transfer_limit_minor BIGINT,
  ADD COLUMN staff_terminal_single_transfer_limit_minor BIGINT,
  ADD COLUMN atm_daily_withdrawal_limit_minor BIGINT,
  ADD COLUMN atm_monthly_withdrawal_limit_minor BIGINT,
  ADD COLUMN atm_single_withdrawal_limit_minor BIGINT;

UPDATE account_limits
SET monthly_transfer_limit_minor = daily_transfer_limit_minor * 31
WHERE monthly_transfer_limit_minor IS NULL;

ALTER TABLE account_limits
  ALTER COLUMN monthly_transfer_limit_minor SET NOT NULL,
  ADD CONSTRAINT account_limits_monthly_transfer_limit_non_negative
    CHECK (monthly_transfer_limit_minor >= 0),
  ADD CONSTRAINT account_limits_channel_limits_non_negative
    CHECK (
      (customer_web_daily_transfer_limit_minor IS NULL OR customer_web_daily_transfer_limit_minor >= 0)
      AND (customer_web_monthly_transfer_limit_minor IS NULL OR customer_web_monthly_transfer_limit_minor >= 0)
      AND (customer_web_single_transfer_limit_minor IS NULL OR customer_web_single_transfer_limit_minor >= 0)
      AND (staff_terminal_daily_transfer_limit_minor IS NULL OR staff_terminal_daily_transfer_limit_minor >= 0)
      AND (staff_terminal_monthly_transfer_limit_minor IS NULL OR staff_terminal_monthly_transfer_limit_minor >= 0)
      AND (staff_terminal_single_transfer_limit_minor IS NULL OR staff_terminal_single_transfer_limit_minor >= 0)
      AND (atm_daily_withdrawal_limit_minor IS NULL OR atm_daily_withdrawal_limit_minor >= 0)
      AND (atm_monthly_withdrawal_limit_minor IS NULL OR atm_monthly_withdrawal_limit_minor >= 0)
      AND (atm_single_withdrawal_limit_minor IS NULL OR atm_single_withdrawal_limit_minor >= 0)
    );

CREATE TABLE limit_usage_counters (
  account_id TEXT NOT NULL REFERENCES accounts(account_id),
  channel TEXT NOT NULL,
  business_date DATE NOT NULL,
  period_kind TEXT NOT NULL CHECK (period_kind IN ('DAILY', 'MONTHLY')),
  used_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (used_amount_minor >= 0),
  version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (account_id, channel, period_kind, business_date)
);

CREATE INDEX idx_limit_usage_counters_account_period
  ON limit_usage_counters(account_id, period_kind, business_date);
