-- Synthetic dispute source references for complaint cases.
-- References remain metadata only; financial corrections still use balanced
-- reversal/adjustment flows instead of mutating source transactions.

ALTER TABLE complaint_cases
  ADD COLUMN source_reference_json JSONB;

ALTER TABLE complaint_cases
  ADD CONSTRAINT complaint_source_reference_synthetic
    CHECK (
      source_reference_json IS NULL
      OR source_reference_json @> '{"syntheticOnly": true}'::jsonb
    );

CREATE INDEX idx_complaint_cases_source_reference
  ON complaint_cases ((source_reference_json->>'sourceId'))
  WHERE source_reference_json IS NOT NULL;
