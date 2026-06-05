CREATE TABLE payment_cancellation_requests (
  cancellation_request_id TEXT PRIMARY KEY,
  payment_instruction_id TEXT NOT NULL REFERENCES payment_instructions(payment_instruction_id),
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  maker_id TEXT NOT NULL,
  maker_role TEXT NOT NULL,
  maker_reason TEXT NOT NULL CHECK (length(trim(maker_reason)) > 0),
  checker_id TEXT,
  checker_role TEXT,
  checker_reason TEXT CHECK (checker_reason IS NULL OR length(trim(checker_reason)) > 0),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_at TIMESTAMPTZ,
  CHECK (
    (status = 'PENDING' AND checker_id IS NULL AND checker_role IS NULL AND checker_reason IS NULL AND decided_at IS NULL)
    OR
    (status IN ('APPROVED', 'REJECTED') AND checker_id IS NOT NULL AND checker_role IS NOT NULL AND checker_reason IS NOT NULL AND decided_at IS NOT NULL AND checker_id <> maker_id)
  )
);

CREATE UNIQUE INDEX idx_payment_cancellation_pending_once
  ON payment_cancellation_requests(payment_instruction_id)
  WHERE status = 'PENDING';

CREATE INDEX idx_payment_cancellation_instruction
  ON payment_cancellation_requests(payment_instruction_id, created_at DESC);

CREATE INDEX idx_payment_cancellation_status
  ON payment_cancellation_requests(status, created_at ASC);
