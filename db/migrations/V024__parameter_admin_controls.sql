-- Effective-dated parameter administration for manifest-backed operations screens.
-- Values are synthetic controls only; no production policy, PII, or external provider data is stored.

CREATE TABLE reconciliation_parameters (
  parameter_key TEXT PRIMARY KEY,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  current_version_id TEXT,
  description TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_parameter_versions (
  parameter_version_id TEXT PRIMARY KEY,
  parameter_key TEXT NOT NULL REFERENCES reconciliation_parameters(parameter_key),
  parameter_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  effective_to DATE,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ,
  rollback_of_version_id TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE audit_retention_parameters (
  parameter_key TEXT PRIMARY KEY,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  current_version_id TEXT,
  description TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_retention_parameter_versions (
  parameter_version_id TEXT PRIMARY KEY,
  parameter_key TEXT NOT NULL REFERENCES audit_retention_parameters(parameter_key),
  parameter_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  effective_to DATE,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ,
  rollback_of_version_id TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE fds_rule_parameters (
  parameter_key TEXT PRIMARY KEY,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  current_version_id TEXT,
  description TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fds_rule_parameter_versions (
  parameter_version_id TEXT PRIMARY KEY,
  parameter_key TEXT NOT NULL REFERENCES fds_rule_parameters(parameter_key),
  parameter_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  effective_to DATE,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ,
  rollback_of_version_id TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE security_policy_parameters (
  parameter_key TEXT PRIMARY KEY,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  current_version_id TEXT,
  description TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE security_policy_parameter_versions (
  parameter_version_id TEXT PRIMARY KEY,
  parameter_key TEXT NOT NULL REFERENCES security_policy_parameters(parameter_key),
  parameter_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  effective_to DATE,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ,
  rollback_of_version_id TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE menu_role_parameters (
  parameter_key TEXT PRIMARY KEY,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  current_version_id TEXT,
  description TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE menu_role_parameter_versions (
  parameter_version_id TEXT PRIMARY KEY,
  parameter_key TEXT NOT NULL REFERENCES menu_role_parameters(parameter_key),
  parameter_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  effective_to DATE,
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  created_by TEXT NOT NULL,
  approved_at TIMESTAMPTZ,
  rollback_of_version_id TEXT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE parameter_change_requests (
  request_id TEXT PRIMARY KEY,
  namespace TEXT NOT NULL CHECK (namespace IN ('reconciliation', 'audit', 'fds', 'security', 'authorization')),
  parameter_key TEXT NOT NULL,
  business_type TEXT NOT NULL,
  approval_id TEXT NOT NULL REFERENCES operator_approvals(approval_id),
  requested_value TEXT NOT NULL,
  value_type TEXT NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT', 'JSON')),
  effective_from DATE NOT NULL,
  rollback_plan TEXT NOT NULL,
  rollback_of_version_id TEXT,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED')),
  idempotency_key TEXT NOT NULL,
  applied_version_id TEXT,
  applied_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (namespace, requested_by, idempotency_key)
);

CREATE INDEX idx_parameter_change_requests_approval ON parameter_change_requests(approval_id);
CREATE INDEX idx_parameter_change_requests_namespace ON parameter_change_requests(namespace, parameter_key, status);
CREATE INDEX idx_reconciliation_parameter_versions_lookup ON reconciliation_parameter_versions(parameter_key, effective_from DESC, created_at DESC);
CREATE INDEX idx_audit_retention_parameter_versions_lookup ON audit_retention_parameter_versions(parameter_key, effective_from DESC, created_at DESC);
CREATE INDEX idx_fds_rule_parameter_versions_lookup ON fds_rule_parameter_versions(parameter_key, effective_from DESC, created_at DESC);
CREATE INDEX idx_security_policy_parameter_versions_lookup ON security_policy_parameter_versions(parameter_key, effective_from DESC, created_at DESC);
CREATE INDEX idx_menu_role_parameter_versions_lookup ON menu_role_parameter_versions(parameter_key, effective_from DESC, created_at DESC);

INSERT INTO reconciliation_parameters (parameter_key, value_type, current_version_id, description)
VALUES
  ('autoMatchToleranceMinor', 'NUMBER', 'RPV-SEED-AUTO-MATCH-TOLERANCE', 'Synthetic reconciliation auto-match tolerance'),
  ('unmatchedItemSlaHours', 'NUMBER', 'RPV-SEED-UNMATCHED-SLA', 'Synthetic unmatched reconciliation SLA hours');

INSERT INTO reconciliation_parameter_versions (parameter_version_id, parameter_key, parameter_value, value_type, effective_from, created_by, approved_at, metadata_json)
VALUES
  ('RPV-SEED-AUTO-MATCH-TOLERANCE', 'autoMatchToleranceMinor', '1000', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('RPV-SEED-UNMATCHED-SLA', 'unmatchedItemSlaHours', '24', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb);

INSERT INTO audit_retention_parameters (parameter_key, value_type, current_version_id, description)
VALUES
  ('retentionYears', 'NUMBER', 'APV-SEED-RETENTION-YEARS', 'Synthetic audit retention period in years'),
  ('hashChainVerificationCadenceHours', 'NUMBER', 'APV-SEED-HASH-CADENCE', 'Synthetic audit hash-chain verification cadence');

INSERT INTO audit_retention_parameter_versions (parameter_version_id, parameter_key, parameter_value, value_type, effective_from, created_by, approved_at, metadata_json)
VALUES
  ('APV-SEED-RETENTION-YEARS', 'retentionYears', '7', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('APV-SEED-HASH-CADENCE', 'hashChainVerificationCadenceHours', '24', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb);

INSERT INTO fds_rule_parameters (parameter_key, value_type, current_version_id, description)
VALUES
  ('highAmountMinor', 'NUMBER', 'FPV-SEED-HIGH-AMOUNT', 'Synthetic FDS high amount hold threshold'),
  ('newDeviceHoldHours', 'NUMBER', 'FPV-SEED-NEW-DEVICE-HOURS', 'Synthetic new device hold duration'),
  ('velocityWindowMinutes', 'NUMBER', 'FPV-SEED-VELOCITY-WINDOW', 'Synthetic transfer velocity window');

INSERT INTO fds_rule_parameter_versions (parameter_version_id, parameter_key, parameter_value, value_type, effective_from, created_by, approved_at, metadata_json)
VALUES
  ('FPV-SEED-HIGH-AMOUNT', 'highAmountMinor', '5000000', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('FPV-SEED-NEW-DEVICE-HOURS', 'newDeviceHoldHours', '24', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('FPV-SEED-VELOCITY-WINDOW', 'velocityWindowMinutes', '60', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb);

INSERT INTO security_policy_parameters (parameter_key, value_type, current_version_id, description)
VALUES
  ('simulatorTokensEnabled', 'BOOLEAN', 'SPV-SEED-SIMULATOR-TOKENS', 'Synthetic simulator-token policy switch'),
  ('passkeyRecoveryDualControlRequired', 'BOOLEAN', 'SPV-SEED-PASSKEY-DUAL-CONTROL', 'Synthetic passkey recovery dual-control policy'),
  ('staffSessionTtlSeconds', 'NUMBER', 'SPV-SEED-STAFF-TTL', 'Synthetic staff session TTL seconds');

INSERT INTO security_policy_parameter_versions (parameter_version_id, parameter_key, parameter_value, value_type, effective_from, created_by, approved_at, metadata_json)
VALUES
  ('SPV-SEED-SIMULATOR-TOKENS', 'simulatorTokensEnabled', 'true', 'BOOLEAN', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('SPV-SEED-PASSKEY-DUAL-CONTROL', 'passkeyRecoveryDualControlRequired', 'true', 'BOOLEAN', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('SPV-SEED-STAFF-TTL', 'staffSessionTtlSeconds', '3600', 'NUMBER', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb);

INSERT INTO menu_role_parameters (parameter_key, value_type, current_version_id, description)
VALUES
  ('roleMenuMap', 'TEXT', 'MPV-SEED-ROLE-MENU', 'Synthetic role-to-menu mapping'),
  ('approvalRoleMatrix', 'TEXT', 'MPV-SEED-APPROVAL-MATRIX', 'Synthetic maker-checker approval-role matrix'),
  ('reasonRequiredScreens', 'TEXT', 'MPV-SEED-REASON-SCREENS', 'Synthetic reason-required screen catalog');

INSERT INTO menu_role_parameter_versions (parameter_version_id, parameter_key, parameter_value, value_type, effective_from, created_by, approved_at, metadata_json)
VALUES
  ('MPV-SEED-ROLE-MENU', 'roleMenuMap', 'BRANCH_STAFF:CST-001,ACC-101;OPS_MANAGER:OPS-101,OPS-301', 'TEXT', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('MPV-SEED-APPROVAL-MATRIX', 'approvalRoleMatrix', 'OPS_MANAGER:RECONCILIATION_PARAMETER_CHANGE;COMPLIANCE_MANAGER:FDS_RULE_PARAMETER_CHANGE', 'TEXT', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb),
  ('MPV-SEED-REASON-SCREENS', 'reasonRequiredScreens', 'OPS-301,AUD-201,FDS-301,ADM-201,ADM-301', 'TEXT', DATE '2026-01-01', 'SEED', TIMESTAMPTZ '2026-01-01T00:00:00Z', '{"syntheticOnly":true}'::jsonb);
