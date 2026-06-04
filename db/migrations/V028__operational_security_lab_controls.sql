-- H5 operational-security lab controls.
-- These tables model WORM export, synthetic KMS/HSM, and PAM break-glass controls for the lab only.
-- They do not store real secrets, real key material, real PII, or external security-provider data.

CREATE TABLE synthetic_kms_keys (
  key_id TEXT PRIMARY KEY,
  purpose TEXT NOT NULL CHECK (purpose IN ('AUDIT_WORM_ANCHOR', 'CARD_PAN_TOKENIZATION', 'SIEM_EXPORT_SIGNING')),
  key_version INTEGER NOT NULL CHECK (key_version > 0),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
  key_material_ref TEXT NOT NULL,
  synthetic_key_hash TEXT NOT NULL,
  activated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  retired_at TIMESTAMPTZ,
  rotated_from_key_id TEXT REFERENCES synthetic_kms_keys(key_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (purpose, key_version)
);

CREATE UNIQUE INDEX idx_synthetic_kms_one_active_per_purpose
  ON synthetic_kms_keys(purpose)
  WHERE status = 'ACTIVE';

INSERT INTO synthetic_kms_keys (
  key_id, purpose, key_version, status, key_material_ref, synthetic_key_hash, metadata_json
) VALUES
  (
    'KMS-SYN-AUDIT-WORM-V1',
    'AUDIT_WORM_ANCHOR',
    1,
    'ACTIVE',
    'synthetic://kms/audit-worm/v1',
    '21034b31cbc6f307a97e00f0492c626cfab4109dbfe07705fb54c8a1deb01573',
    '{"syntheticOnly":true,"material":"not-real-key-material"}'::jsonb
  ),
  (
    'KMS-SYN-CARD-PAN-V1',
    'CARD_PAN_TOKENIZATION',
    1,
    'ACTIVE',
    'synthetic://kms/card-pan/v1',
    '0c30607e9924926a0d5d63a634ad53d87fb50a1edbd44302e2307cca43aa5356',
    '{"syntheticOnly":true,"material":"not-real-key-material"}'::jsonb
  ),
  (
    'KMS-SYN-SIEM-EXPORT-V1',
    'SIEM_EXPORT_SIGNING',
    1,
    'ACTIVE',
    'synthetic://kms/siem-export/v1',
    'e1d049459bbee2d779a06c1d4a6b9083808ac2e56c88aa072ccb538bdf7d1b5a',
    '{"syntheticOnly":true,"material":"not-real-key-material"}'::jsonb
  );

CREATE TABLE audit_worm_export_segments (
  segment_id TEXT PRIMARY KEY,
  segment_no BIGINT NOT NULL UNIQUE CHECK (segment_no > 0),
  from_audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  through_audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  audit_event_ids JSONB NOT NULL,
  event_count INTEGER NOT NULL CHECK (event_count >= 0),
  previous_anchor_hash TEXT,
  segment_payload_hash TEXT NOT NULL,
  segment_hash TEXT NOT NULL,
  signing_key_id TEXT NOT NULL REFERENCES synthetic_kms_keys(key_id),
  exported_by TEXT NOT NULL,
  exported_role TEXT NOT NULL,
  export_reason TEXT NOT NULL,
  storage_uri TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('SEALED')),
  exported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT audit_worm_event_id_array CHECK (jsonb_typeof(audit_event_ids) = 'array')
);

CREATE INDEX idx_audit_worm_export_segments_exported_at
  ON audit_worm_export_segments(exported_at);

CREATE TABLE break_glass_review_cases (
  review_case_id TEXT PRIMARY KEY,
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  due_at TIMESTAMPTZ NOT NULL,
  reviewer_id TEXT,
  reviewer_role TEXT,
  review_reason TEXT,
  reviewed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE break_glass_grants (
  grant_id TEXT PRIMARY KEY,
  operator_id TEXT NOT NULL,
  operator_role TEXT NOT NULL,
  elevated_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVIEWED', 'REVOKED')),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  review_case_id TEXT NOT NULL UNIQUE REFERENCES break_glass_review_cases(review_case_id),
  audit_event_id TEXT REFERENCES audit_events(audit_event_id),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_break_glass_grants_operator_status
  ON break_glass_grants(operator_id, status, expires_at);

CREATE INDEX idx_break_glass_review_cases_status
  ON break_glass_review_cases(status, due_at);
