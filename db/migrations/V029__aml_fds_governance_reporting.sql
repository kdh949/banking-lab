-- H6 AML/FDS governance and reporting controls.
-- Watchlists, model metadata, STRs, and regulatory reports are synthetic lab simulations only.
-- Do not load real sanctions, PEP, customer PII, KYC, payment-network, or regulator data.

CREATE TABLE synthetic_watchlist_entries (
  watchlist_entry_id TEXT PRIMARY KEY,
  list_type TEXT NOT NULL CHECK (list_type IN ('SANCTIONS_SIM', 'PEP_SIM')),
  display_name TEXT NOT NULL,
  normalized_name TEXT NOT NULL,
  risk_score INTEGER NOT NULL CHECK (risk_score BETWEEN 0 AND 1000),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
  source_reference TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (list_type, normalized_name)
);

INSERT INTO synthetic_watchlist_entries (
  watchlist_entry_id, list_type, display_name, normalized_name, risk_score, status, source_reference, metadata_json
) VALUES
  (
    'SWL-SYN-SANCTIONS-001',
    'SANCTIONS_SIM',
    'Synthetic Sanction Match',
    'synthetic sanction match',
    980,
    'ACTIVE',
    'synthetic://watchlist/sanctions/001',
    '{"syntheticOnly":true,"realSanctionsData":false}'::jsonb
  ),
  (
    'SWL-SYN-PEP-001',
    'PEP_SIM',
    'Synthetic PEP Match',
    'synthetic pep match',
    760,
    'ACTIVE',
    'synthetic://watchlist/pep/001',
    '{"syntheticOnly":true,"realPepData":false}'::jsonb
  );

CREATE TABLE sanctions_screening_hits (
  hit_id TEXT PRIMARY KEY,
  watchlist_entry_id TEXT NOT NULL REFERENCES synthetic_watchlist_entries(watchlist_entry_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  transfer_reference_id TEXT,
  aml_case_id TEXT REFERENCES aml_cases(aml_case_id),
  match_type TEXT NOT NULL CHECK (match_type IN ('CUSTOMER_ONBOARDING', 'TRANSFER_COUNTERPARTY')),
  matched_value TEXT NOT NULL,
  risk_score INTEGER NOT NULL CHECK (risk_score BETWEEN 0 AND 1000),
  status TEXT NOT NULL CHECK (status IN ('OPEN', 'DISPOSITIONED')),
  disposition TEXT CHECK (disposition IN ('FALSE_POSITIVE', 'TRUE_MATCH_SIMULATED')),
  disposition_reason TEXT,
  disposition_by TEXT,
  approved_by TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  dispositioned_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_sanctions_screening_hits_customer
  ON sanctions_screening_hits(customer_id, status, created_at);

CREATE TABLE aml_model_versions (
  model_version_id TEXT PRIMARY KEY,
  model_name TEXT NOT NULL,
  version_label TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
  features_json JSONB NOT NULL,
  score_distribution_json JSONB NOT NULL,
  drift_check_json JSONB NOT NULL,
  explainability_json JSONB NOT NULL,
  training_data_boundary TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (model_name, version_label)
);

INSERT INTO aml_model_versions (
  model_version_id, model_name, version_label, status,
  features_json, score_distribution_json, drift_check_json, explainability_json, training_data_boundary
) VALUES (
  'AML-MODEL-SYN-RULES-V1',
  'banking_lab_aml_fds_rules',
  '2026-h6-synthetic-rules-v1',
  'ACTIVE',
  '["amount_minor","risk_grade","velocity_24h","first_time_beneficiary","new_device","amount_to_median_ratio"]'::jsonb,
  '{"LOW":8,"MEDIUM":2,"HIGH":2}'::jsonb,
  '{"status":"pass","method":"fixed synthetic baseline","populationStabilityIndex":0.0}'::jsonb,
  '{"method":"deterministic rules + anomaly reasons","notes":"No real customer data or real sanctions data used."}'::jsonb,
  'synthetic sample transactions only; no real PII, KYC, sanctions, or payment-network data'
);

CREATE TABLE aml_case_evidence_packages (
  evidence_package_id TEXT PRIMARY KEY,
  case_type TEXT NOT NULL CHECK (case_type IN ('AML', 'FDS')),
  case_id TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  package_json JSONB NOT NULL,
  generated_by TEXT NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE synthetic_str_reports (
  str_report_id TEXT PRIMARY KEY,
  aml_case_id TEXT NOT NULL,
  evidence_package_id TEXT REFERENCES aml_case_evidence_packages(evidence_package_id),
  report_json JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('GENERATED', 'DISPOSITIONED_FALSE_POSITIVE')),
  generated_by TEXT NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE synthetic_aml_regulatory_reports (
  regulatory_report_id TEXT PRIMARY KEY,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  report_json JSONB NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('GENERATED')),
  generated_by TEXT NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  CHECK (period_end >= period_start)
);
