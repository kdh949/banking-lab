-- Synthetic reporting-service foundation.
-- Report artifacts start from a synthetic metadata foundation; later migrations add rendered payload controls.

CREATE TABLE report_definitions (
  report_type TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  category TEXT NOT NULL,
  default_masking_policy TEXT NOT NULL,
  sensitive BOOLEAN NOT NULL DEFAULT true,
  source_systems JSONB NOT NULL,
  synthetic_only BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE report_artifacts (
  artifact_id TEXT PRIMARY KEY,
  report_type TEXT NOT NULL REFERENCES report_definitions(report_type),
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('GENERATED')),
  artifact_path TEXT NOT NULL,
  source_references JSONB NOT NULL,
  masked_by_default BOOLEAN NOT NULL DEFAULT true,
  synthetic_only BOOLEAN NOT NULL DEFAULT true,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (requested_by, idempotency_key)
);

CREATE TABLE reporting_access_audit_events (
  audit_event_id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  report_type TEXT,
  artifact_id TEXT,
  payload_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO report_definitions (
  report_type, title, category, default_masking_policy, sensitive, source_systems
) VALUES
  (
    'AUDIT_SUMMARY',
    'Synthetic Audit Summary',
    'AUDIT',
    'MASK_ACTOR_AND_REFERENCE',
    true,
    '["audit_events","reporting_access_audit_events"]'::jsonb
  ),
  (
    'OPERATIONS_DAILY',
    'Synthetic Daily Operations Summary',
    'OPERATIONS',
    'MASK_OPERATIONAL_REFERENCES',
    true,
    '["daily_closings","outbox_events","workflow_instances"]'::jsonb
  ),
  (
    'EVIDENCE_COVERAGE',
    'Synthetic Evidence Coverage Report',
    'EVIDENCE',
    'NO_PII_SOURCE_METADATA_ONLY',
    false,
    '["docs/implementation-coverage-matrix.md","docs/test-evidence"]'::jsonb
  )
ON CONFLICT (report_type) DO NOTHING;

CREATE INDEX idx_report_artifacts_type_generated
  ON report_artifacts(report_type, generated_at DESC);

CREATE INDEX idx_reporting_access_audit_events_type
  ON reporting_access_audit_events(event_type, created_at DESC);
