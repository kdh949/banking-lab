-- Durable held-transfer details for FDS release/block execution parity.

ALTER TABLE fds_cases
  ADD COLUMN from_account_id TEXT REFERENCES accounts(account_id),
  ADD COLUMN to_account_id TEXT REFERENCES accounts(account_id),
  ADD COLUMN amount_minor BIGINT CHECK (amount_minor IS NULL OR amount_minor > 0),
  ADD COLUMN transfer_idempotency_key TEXT,
  ADD COLUMN requested_by TEXT,
  ADD COLUMN business_date DATE,
  ADD COLUMN transfer_status TEXT CHECK (transfer_status IS NULL OR transfer_status IN ('HELD', 'POSTED', 'BLOCKED'));
