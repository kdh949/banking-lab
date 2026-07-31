ALTER TABLE payment_instructions
  DROP CONSTRAINT IF EXISTS payment_instructions_status_check;
ALTER TABLE payment_attempts
  DROP CONSTRAINT IF EXISTS payment_attempts_status_check;
ALTER TABLE payment_status_history
  DROP CONSTRAINT IF EXISTS payment_status_history_status_check;

UPDATE payment_instructions
SET status = 'LEDGER_POSTED',
    updated_at = now()
WHERE status = 'SETTLED';

UPDATE payment_attempts
SET status = 'LEDGER_POSTED'
WHERE status = 'SETTLED';

UPDATE payment_status_history
SET status = 'LEDGER_POSTED'
WHERE status = 'SETTLED';

UPDATE payment_idempotency_keys
SET response_json = jsonb_set(
  response_json,
  '{item,status}',
  to_jsonb('LEDGER_POSTED'::text),
  false
)
WHERE response_json #>> '{item,status}' = 'SETTLED';

UPDATE payment_idempotency_keys
SET response_json = jsonb_set(
  response_json,
  '{instruction,status}',
  to_jsonb('LEDGER_POSTED'::text),
  false
)
WHERE response_json #>> '{instruction,status}' = 'SETTLED';

UPDATE payment_idempotency_keys
SET command_type = 'RECORD_PAYMENT_LEDGER_POSTING'
WHERE command_type = 'RECORD_PAYMENT_SETTLEMENT';

UPDATE payment_outbox_events
SET event_type = 'PaymentInstructionLedgerPosted',
    payload_json = jsonb_set(
      jsonb_set(
        jsonb_set(
          payload_json,
          '{contractVersion}',
          to_jsonb('2026-07-31'::text),
          true
        ),
        '{status}',
        to_jsonb('LEDGER_POSTED'::text),
        true
      ),
      '{externalSettlementCompleted}',
      'false'::jsonb,
      true
    )
WHERE event_type = 'PaymentInstructionSettled'
  AND status IN ('PENDING', 'FAILED');

ALTER TABLE payment_instructions
  ADD CONSTRAINT payment_instructions_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));
ALTER TABLE payment_attempts
  ADD CONSTRAINT payment_attempts_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));
ALTER TABLE payment_status_history
  ADD CONSTRAINT payment_status_history_status_check
  CHECK (status IN ('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'));

COMMENT ON COLUMN payment_instructions.status IS
  'LEDGER_POSTED means core-banking accepted the internal ledger posting; external clearing or settlement is not represented by this aggregate.';
COMMENT ON COLUMN payment_instructions.ledger_transaction_id IS
  'Synthetic core-banking ledger transaction reference; not an external settlement or payout reference.';
