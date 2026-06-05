ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_channel_check;
ALTER TABLE notification_templates
  ADD CONSTRAINT notification_templates_channel_check
  CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'CHAT'));

ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_provider_kind_check;
ALTER TABLE notification_templates
  ADD CONSTRAINT notification_templates_provider_kind_check
  CHECK (provider_kind IN (
    'SYNTHETIC_SMS_SINK',
    'SYNTHETIC_EMAIL_SINK',
    'SYNTHETIC_PUSH_SINK',
    'SYNTHETIC_CHAT_SINK'
  ));

ALTER TABLE notification_delivery_requests DROP CONSTRAINT IF EXISTS notification_delivery_requests_channel_check;
ALTER TABLE notification_delivery_requests
  ADD CONSTRAINT notification_delivery_requests_channel_check
  CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'CHAT'));

ALTER TABLE notification_delivery_requests DROP CONSTRAINT IF EXISTS notification_delivery_requests_provider_kind_check;
ALTER TABLE notification_delivery_requests
  ADD CONSTRAINT notification_delivery_requests_provider_kind_check
  CHECK (provider_kind IN (
    'SYNTHETIC_SMS_SINK',
    'SYNTHETIC_EMAIL_SINK',
    'SYNTHETIC_PUSH_SINK',
    'SYNTHETIC_CHAT_SINK'
  ));

ALTER TABLE notification_delivery_attempts DROP CONSTRAINT IF EXISTS notification_delivery_attempts_provider_kind_check;
ALTER TABLE notification_delivery_attempts
  ADD CONSTRAINT notification_delivery_attempts_provider_kind_check
  CHECK (provider_kind IN (
    'SYNTHETIC_SMS_SINK',
    'SYNTHETIC_EMAIL_SINK',
    'SYNTHETIC_PUSH_SINK',
    'SYNTHETIC_CHAT_SINK'
  ));

CREATE TABLE notification_template_change_requests (
  change_request_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'CHAT')),
  requested_version INTEGER NOT NULL CHECK (requested_version > 0),
  body_template TEXT NOT NULL,
  provider_kind TEXT NOT NULL CHECK (provider_kind IN (
    'SYNTHETIC_SMS_SINK',
    'SYNTHETIC_EMAIL_SINK',
    'SYNTHETIC_PUSH_SINK',
    'SYNTHETIC_CHAT_SINK'
  )),
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  requested_by TEXT NOT NULL,
  request_reason TEXT NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_by TEXT,
  reviewed_by_role TEXT,
  reviewed_at TIMESTAMPTZ,
  review_reason TEXT,
  approved_template_id TEXT REFERENCES notification_templates(template_id),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  CONSTRAINT notification_template_review_fields CHECK (
    (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL)
    OR
    (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL)
  )
);

CREATE INDEX idx_notification_template_change_status
  ON notification_template_change_requests(status, requested_at DESC);

CREATE INDEX idx_notification_template_change_target
  ON notification_template_change_requests(event_type, channel, requested_version);
