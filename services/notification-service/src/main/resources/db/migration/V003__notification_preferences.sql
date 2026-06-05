CREATE TABLE notification_recipient_preferences (
  preference_id TEXT PRIMARY KEY,
  recipient_id TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'CHAT')),
  event_type TEXT NOT NULL DEFAULT '*' CHECK (event_type <> ''),
  enabled BOOLEAN NOT NULL,
  requested_by TEXT NOT NULL,
  reason TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notification_preference_target_once UNIQUE (recipient_id, channel, event_type)
);

CREATE TABLE notification_suppressed_events (
  suppression_id TEXT PRIMARY KEY,
  consumer_name TEXT NOT NULL,
  source_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  recipient_id TEXT NOT NULL,
  channel TEXT NOT NULL CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'CHAT')),
  preference_id TEXT NOT NULL REFERENCES notification_recipient_preferences(preference_id),
  reason TEXT NOT NULL,
  masked_payload_json JSONB NOT NULL,
  requested_by TEXT NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notification_suppression_event_once UNIQUE (consumer_name, source_event_id)
);

CREATE TABLE notification_access_audit_events (
  audit_event_id TEXT PRIMARY KEY,
  action TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id TEXT NOT NULL,
  details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_preferences_recipient
  ON notification_recipient_preferences(recipient_id, channel, event_type);

CREATE INDEX idx_notification_suppressed_recipient
  ON notification_suppressed_events(recipient_id, channel, created_at DESC);

CREATE INDEX idx_notification_access_audit_target
  ON notification_access_audit_events(target_type, target_id, created_at DESC);
