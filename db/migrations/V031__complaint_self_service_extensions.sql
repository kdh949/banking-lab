-- Customer self-service complaint extension metadata.
-- Stores synthetic attachment metadata and reopen requests without real files or PII.

CREATE TABLE complaint_case_materials (
  complaint_material_id TEXT PRIMARY KEY,
  complaint_case_id TEXT NOT NULL REFERENCES complaint_cases(complaint_case_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  material_type TEXT NOT NULL,
  file_name TEXT NOT NULL,
  description TEXT,
  synthetic_storage_ref TEXT NOT NULL,
  submitted_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT complaint_material_type_not_blank CHECK (length(trim(material_type)) > 0),
  CONSTRAINT complaint_material_file_name_not_blank CHECK (length(trim(file_name)) > 0),
  CONSTRAINT complaint_material_synthetic_ref CHECK (synthetic_storage_ref LIKE 'synthetic://%')
);

CREATE TABLE complaint_reopen_requests (
  complaint_reopen_request_id TEXT PRIMARY KEY,
  complaint_case_id TEXT NOT NULL REFERENCES complaint_cases(complaint_case_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  reopen_reason TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('REOPENED', 'REJECTED')),
  requested_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT complaint_reopen_reason_not_blank CHECK (length(trim(reopen_reason)) > 0)
);

CREATE INDEX idx_complaint_materials_case_created ON complaint_case_materials(complaint_case_id, created_at);
CREATE INDEX idx_complaint_reopen_requests_case_created ON complaint_reopen_requests(complaint_case_id, created_at);
