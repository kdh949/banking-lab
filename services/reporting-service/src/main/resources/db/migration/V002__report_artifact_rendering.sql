-- Synthetic report artifacts now persist rendered JSON payloads and retention/export metadata.
-- Payloads remain synthetic-only and masked by default; no real filings or customer documents are generated.

ALTER TABLE report_artifacts
  ADD COLUMN artifact_content JSONB NOT NULL DEFAULT '{
    "schemaVersion": 1,
    "syntheticOnly": true,
    "maskedByDefault": true,
    "controls": {
      "realPiiUsed": false,
      "realMoneyUsed": false,
      "externalFilingSubmitted": false,
      "ledgerRowsMutated": false
    }
  }'::jsonb,
  ADD COLUMN content_sha256 TEXT NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',
  ADD COLUMN retention_policy TEXT NOT NULL DEFAULT 'SYNTHETIC_7Y',
  ADD COLUMN retention_until DATE NOT NULL DEFAULT ((CURRENT_DATE + INTERVAL '7 years')::date),
  ADD COLUMN export_format TEXT NOT NULL DEFAULT 'JSON';

ALTER TABLE report_artifacts
  ADD CONSTRAINT chk_report_artifacts_synthetic_rendered_content
  CHECK (
    artifact_content ? 'syntheticOnly'
    AND artifact_content ? 'maskedByDefault'
    AND (artifact_content ->> 'syntheticOnly')::boolean = true
    AND (artifact_content ->> 'maskedByDefault')::boolean = true
  ),
  ADD CONSTRAINT chk_report_artifacts_content_sha256
  CHECK (content_sha256 ~ '^[a-f0-9]{64}$'),
  ADD CONSTRAINT chk_report_artifacts_export_format
  CHECK (export_format IN ('JSON')),
  ADD CONSTRAINT chk_report_artifacts_retention_policy
  CHECK (retention_policy IN ('SYNTHETIC_7Y'));
