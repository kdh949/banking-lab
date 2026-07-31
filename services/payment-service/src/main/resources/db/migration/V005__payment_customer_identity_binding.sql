ALTER TABLE payment_autopay_agreements
  ADD COLUMN customer_identity_bound BOOLEAN NOT NULL DEFAULT false;

INSERT INTO payment_autopay_status_history (
  autopay_status_history_id,
  autopay_agreement_id,
  status,
  actor_id,
  reason,
  changed_at
)
SELECT
  'APSH-V005-' || agreement.autopay_agreement_id,
  agreement.autopay_agreement_id,
  'PAUSED',
  'payment-customer-identity-migration',
  'Legacy autopay paused because customer identity was not server-bound',
  now()
FROM payment_autopay_agreements agreement
WHERE agreement.status = 'ACTIVE'
ON CONFLICT (autopay_status_history_id) DO NOTHING;

UPDATE payment_idempotency_keys idempotency
SET response_json = jsonb_set(
  jsonb_set(idempotency.response_json, '{item,status}', to_jsonb('PAUSED'::text), false),
  '{item,updatedAt}', to_jsonb(now()), false
)
WHERE idempotency.command_type = 'CREATE_AUTOPAY_AGREEMENT'
  AND idempotency.aggregate_id IN (
    SELECT agreement.autopay_agreement_id
    FROM payment_autopay_agreements agreement
    WHERE agreement.status = 'ACTIVE'
  );

UPDATE payment_autopay_agreements
SET status = 'PAUSED',
    updated_at = now()
WHERE status = 'ACTIVE';

ALTER TABLE payment_autopay_agreements
  ALTER COLUMN customer_identity_bound SET DEFAULT true;

ALTER TABLE payment_autopay_agreements
  ADD CONSTRAINT payment_autopay_active_identity_bound
  CHECK (customer_identity_bound = true OR status <> 'ACTIVE');

INSERT INTO payment_status_history (
  payment_status_history_id,
  payment_instruction_id,
  status,
  actor_id,
  reason,
  changed_at
)
SELECT
  'PSH-V005-' || event.outbox_event_id,
  event.aggregate_id,
  'FAILED',
  'payment-customer-identity-migration',
  'Legacy payment posting quarantined because customer identity was not server-bound',
  now()
FROM payment_outbox_events event
JOIN payment_instructions instruction
  ON instruction.payment_instruction_id = event.aggregate_id
WHERE event.event_type = 'PaymentLedgerPostingRequested'
  AND event.status IN ('PENDING', 'FAILED')
  AND instruction.status = 'POSTING_REQUESTED'
ON CONFLICT (payment_status_history_id) DO NOTHING;

UPDATE payment_idempotency_keys idempotency
SET response_json = jsonb_set(
  jsonb_set(idempotency.response_json, '{item,status}', to_jsonb('FAILED'::text), false),
  '{item,updatedAt}', to_jsonb(now()), false
)
WHERE idempotency.command_type = 'CREATE_PAYMENT_INSTRUCTION'
  AND idempotency.aggregate_id IN (
    SELECT event.aggregate_id
    FROM payment_outbox_events event
    WHERE event.event_type = 'PaymentLedgerPostingRequested'
      AND event.status IN ('PENDING', 'FAILED')
  );

UPDATE payment_attempts attempt
SET status = 'FAILED',
    completed_at = now()
WHERE attempt.status = 'POSTING_REQUESTED'
  AND attempt.payment_instruction_id IN (
    SELECT event.aggregate_id
    FROM payment_outbox_events event
    WHERE event.event_type = 'PaymentLedgerPostingRequested'
      AND event.status IN ('PENDING', 'FAILED')
  );

UPDATE payment_instructions instruction
SET status = 'FAILED',
    updated_at = now()
WHERE instruction.status = 'POSTING_REQUESTED'
  AND instruction.payment_instruction_id IN (
    SELECT event.aggregate_id
    FROM payment_outbox_events event
    WHERE event.event_type = 'PaymentLedgerPostingRequested'
      AND event.status IN ('PENDING', 'FAILED')
  );

UPDATE payment_outbox_events
SET status = 'DEAD_LETTER',
    retry_count = GREATEST(retry_count, 1),
    next_retry_at = NULL,
    error_message = 'legacy payment posting quarantined: customer identity was not server-bound'
WHERE event_type = 'PaymentLedgerPostingRequested'
  AND status IN ('PENDING', 'FAILED');

COMMENT ON COLUMN payment_autopay_agreements.customer_identity_bound IS
  'True only for agreements created after customer identity binding was enabled; legacy agreements remain paused.';
