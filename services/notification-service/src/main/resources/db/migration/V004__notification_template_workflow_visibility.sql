CREATE TABLE notification_workflow_instances (
  workflow_instance_id TEXT PRIMARY KEY,
  workflow_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
  started_by TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  CONSTRAINT notification_workflow_reference_once UNIQUE (workflow_type, business_reference_id)
);

CREATE TABLE notification_workflow_events (
  workflow_event_id TEXT PRIMARY KEY,
  workflow_instance_id TEXT NOT NULL REFERENCES notification_workflow_instances(workflow_instance_id),
  event_type TEXT NOT NULL CHECK (event_type IN ('REQUESTED', 'APPROVED', 'REJECTED')),
  from_status TEXT,
  to_status TEXT NOT NULL CHECK (to_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
  actor_id TEXT NOT NULL,
  reason TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true)
);

ALTER TABLE notification_template_change_requests
  ADD COLUMN workflow_instance_id TEXT;

INSERT INTO notification_workflow_instances (
  workflow_instance_id, workflow_type, business_reference_id, status,
  started_by, started_at, completed_at, synthetic_only
)
SELECT
  'NWF-' || change_request_id,
  'NOTIFICATION_TEMPLATE_CHANGE',
  change_request_id,
  CASE status
    WHEN 'PENDING' THEN 'PENDING_REVIEW'
    WHEN 'APPROVED' THEN 'APPROVED'
    ELSE 'REJECTED'
  END,
  requested_by,
  requested_at,
  reviewed_at,
  true
FROM notification_template_change_requests
ON CONFLICT (workflow_type, business_reference_id) DO NOTHING;

UPDATE notification_template_change_requests
SET workflow_instance_id = 'NWF-' || change_request_id
WHERE workflow_instance_id IS NULL;

INSERT INTO notification_workflow_events (
  workflow_event_id, workflow_instance_id, event_type, from_status,
  to_status, actor_id, reason, occurred_at, synthetic_only
)
SELECT
  'NWE-' || change_request_id || '-REQUESTED',
  workflow_instance_id,
  'REQUESTED',
  NULL,
  'PENDING_REVIEW',
  requested_by,
  request_reason,
  requested_at,
  true
FROM notification_template_change_requests
ON CONFLICT (workflow_event_id) DO NOTHING;

INSERT INTO notification_workflow_events (
  workflow_event_id, workflow_instance_id, event_type, from_status,
  to_status, actor_id, reason, occurred_at, synthetic_only
)
SELECT
  'NWE-' || change_request_id || '-' || status,
  workflow_instance_id,
  status,
  'PENDING_REVIEW',
  CASE status WHEN 'APPROVED' THEN 'APPROVED' ELSE 'REJECTED' END,
  reviewed_by,
  review_reason,
  reviewed_at,
  true
FROM notification_template_change_requests
WHERE status IN ('APPROVED', 'REJECTED')
ON CONFLICT (workflow_event_id) DO NOTHING;

ALTER TABLE notification_template_change_requests
  ALTER COLUMN workflow_instance_id SET NOT NULL,
  ADD CONSTRAINT notification_template_change_workflow_fk
    FOREIGN KEY (workflow_instance_id)
    REFERENCES notification_workflow_instances(workflow_instance_id);

CREATE INDEX idx_notification_workflow_instances_reference
  ON notification_workflow_instances(workflow_type, business_reference_id, status);

CREATE INDEX idx_notification_workflow_events_instance_time
  ON notification_workflow_events(workflow_instance_id, occurred_at ASC, workflow_event_id ASC);
