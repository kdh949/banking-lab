CREATE TABLE notification_templates (
  template_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('SMS', 'EMAIL', 'PUSH')),
  version INTEGER NOT NULL CHECK (version > 0),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
  body_template TEXT NOT NULL,
  provider_kind TEXT NOT NULL CHECK (provider_kind IN ('SYNTHETIC_SMS_SINK', 'SYNTHETIC_EMAIL_SINK', 'SYNTHETIC_PUSH_SINK')),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notification_template_version_once UNIQUE (event_type, channel, version)
);

CREATE TABLE notification_inbox_events (
  consumer_name TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer_name, source_event_id)
);

CREATE TABLE notification_delivery_requests (
  delivery_request_id TEXT PRIMARY KEY,
  source_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  recipient_id TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('SMS', 'EMAIL', 'PUSH')),
  provider_kind TEXT NOT NULL CHECK (provider_kind IN ('SYNTHETIC_SMS_SINK', 'SYNTHETIC_EMAIL_SINK', 'SYNTHETIC_PUSH_SINK')),
  template_id TEXT NOT NULL REFERENCES notification_templates(template_id),
  masked_payload_json JSONB NOT NULL,
  masked_message TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'DEAD_LETTER')),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_delivery_attempts (
  delivery_attempt_id TEXT PRIMARY KEY,
  delivery_request_id TEXT NOT NULL REFERENCES notification_delivery_requests(delivery_request_id),
  attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
  provider_kind TEXT NOT NULL CHECK (provider_kind IN ('SYNTHETIC_SMS_SINK', 'SYNTHETIC_EMAIL_SINK', 'SYNTHETIC_PUSH_SINK')),
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'DEAD_LETTER')),
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  error_message TEXT,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  CONSTRAINT notification_attempt_once UNIQUE (delivery_request_id, attempt_no)
);

CREATE TABLE notification_dead_letters (
  dead_letter_id TEXT PRIMARY KEY,
  delivery_request_id TEXT NOT NULL REFERENCES notification_delivery_requests(delivery_request_id),
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_deliveries_source ON notification_delivery_requests(source_event_id);
CREATE INDEX idx_notification_deliveries_status ON notification_delivery_requests(status, updated_at DESC);

INSERT INTO notification_templates (
  template_id, event_type, channel, version, status, body_template, provider_kind, synthetic_only
)
VALUES
  ('NTPL-SMS-LEDGER-POSTED-V1', 'LedgerTransactionPosted', 'SMS', 1, 'ACTIVE', 'Synthetic ledger event {transactionId} posted {amountMinor} {currency} for {accountNo}.', 'SYNTHETIC_SMS_SINK', true),
  ('NTPL-SMS-PAYMENT-REQUESTED-V1', 'PaymentLedgerPostingRequested', 'SMS', 1, 'ACTIVE', 'Synthetic payment request {paymentInstructionId} accepted for {amountMinor} {currency} from {accountNo}.', 'SYNTHETIC_SMS_SINK', true),
  ('NTPL-PUSH-COMPLAINT-ANSWERED-V1', 'ComplaintAnswered', 'PUSH', 1, 'ACTIVE', 'Synthetic complaint {caseId} has an approved answer.', 'SYNTHETIC_PUSH_SINK', true);
