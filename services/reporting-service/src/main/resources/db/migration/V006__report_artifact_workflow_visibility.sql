CREATE TABLE reporting_workflow_instances (
  workflow_instance_id TEXT PRIMARY KEY,
  workflow_type TEXT NOT NULL,
  business_reference_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('GENERATED', 'EXPORTED', 'EXPIRED')),
  started_by TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true),
  CONSTRAINT reporting_workflow_reference_once UNIQUE (workflow_type, business_reference_id)
);

CREATE TABLE reporting_workflow_events (
  workflow_event_id TEXT PRIMARY KEY,
  workflow_instance_id TEXT NOT NULL REFERENCES reporting_workflow_instances(workflow_instance_id),
  event_type TEXT NOT NULL CHECK (event_type IN ('GENERATED', 'EXPORTED', 'EXPIRED')),
  from_status TEXT CHECK (from_status IS NULL OR from_status IN ('GENERATED', 'EXPORTED', 'EXPIRED')),
  to_status TEXT NOT NULL CHECK (to_status IN ('GENERATED', 'EXPORTED', 'EXPIRED')),
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  synthetic_only BOOLEAN NOT NULL DEFAULT true CHECK (synthetic_only = true)
);

ALTER TABLE report_artifacts
  ADD COLUMN workflow_instance_id TEXT;

INSERT INTO reporting_workflow_instances (
  workflow_instance_id, workflow_type, business_reference_id, status,
  started_by, started_at, completed_at, synthetic_only
)
SELECT
  'RWF-' || artifact_id,
  'REPORT_ARTIFACT_LIFECYCLE',
  artifact_id,
  status,
  requested_by,
  generated_at,
  expired_at,
  true
FROM report_artifacts
ON CONFLICT (workflow_type, business_reference_id) DO NOTHING;

UPDATE report_artifacts
SET workflow_instance_id = 'RWF-' || artifact_id
WHERE workflow_instance_id IS NULL;

INSERT INTO reporting_workflow_events (
  workflow_event_id, workflow_instance_id, event_type, from_status,
  to_status, actor_id, actor_role, reason, occurred_at, synthetic_only
)
SELECT
  'RWE-' || artifact_id || '-GENERATED',
  workflow_instance_id,
  'GENERATED',
  NULL,
  'GENERATED',
  requested_by,
  requested_role,
  reason,
  generated_at,
  true
FROM report_artifacts
ON CONFLICT (workflow_event_id) DO NOTHING;

INSERT INTO reporting_workflow_events (
  workflow_event_id, workflow_instance_id, event_type, from_status,
  to_status, actor_id, actor_role, reason, occurred_at, synthetic_only
)
SELECT DISTINCT ON (ra.artifact_id)
  'RWE-' || ra.artifact_id || '-EXPORTED',
  ra.workflow_instance_id,
  'EXPORTED',
  'GENERATED',
  'EXPORTED',
  audit.actor_id,
  audit.actor_role,
  audit.reason,
  audit.created_at,
  true
FROM report_artifacts ra
JOIN reporting_access_audit_events audit
  ON audit.event_type = 'REPORT_ARTIFACT_EXPORTED'
 AND audit.artifact_id = ra.artifact_id
ORDER BY ra.artifact_id, audit.created_at ASC, audit.audit_event_id ASC
ON CONFLICT (workflow_event_id) DO NOTHING;

INSERT INTO reporting_workflow_events (
  workflow_event_id, workflow_instance_id, event_type, from_status,
  to_status, actor_id, actor_role, reason, occurred_at, synthetic_only
)
SELECT
  'RWE-' || artifact_id || '-EXPIRED',
  workflow_instance_id,
  'EXPIRED',
  CASE
    WHEN EXISTS (
      SELECT 1
      FROM reporting_workflow_events exported_event
      WHERE exported_event.workflow_instance_id = report_artifacts.workflow_instance_id
        AND exported_event.event_type = 'EXPORTED'
    )
    THEN 'EXPORTED'
    ELSE 'GENERATED'
  END,
  'EXPIRED',
  requested_by,
  requested_role,
  COALESCE(retention_action, 'SYNTHETIC_RETENTION_EXPIRED'),
  expired_at,
  true
FROM report_artifacts
WHERE status = 'EXPIRED'
ON CONFLICT (workflow_event_id) DO NOTHING;

UPDATE reporting_workflow_instances workflow
SET status = CASE
      WHEN artifact.status = 'EXPIRED' THEN 'EXPIRED'
      WHEN EXISTS (
        SELECT 1
        FROM reporting_workflow_events exported_event
        WHERE exported_event.workflow_instance_id = workflow.workflow_instance_id
          AND exported_event.event_type = 'EXPORTED'
      ) THEN 'EXPORTED'
      ELSE 'GENERATED'
    END,
    completed_at = CASE
      WHEN artifact.status = 'EXPIRED' THEN artifact.expired_at
      ELSE NULL
    END
FROM report_artifacts artifact
WHERE artifact.workflow_instance_id = workflow.workflow_instance_id;

ALTER TABLE report_artifacts
  ALTER COLUMN workflow_instance_id SET NOT NULL,
  ADD CONSTRAINT report_artifacts_workflow_fk
    FOREIGN KEY (workflow_instance_id)
    REFERENCES reporting_workflow_instances(workflow_instance_id);

CREATE INDEX idx_reporting_workflow_instances_reference
  ON reporting_workflow_instances(workflow_type, business_reference_id, status);

CREATE INDEX idx_reporting_workflow_events_instance_time
  ON reporting_workflow_events(workflow_instance_id, occurred_at ASC, workflow_event_id ASC);
