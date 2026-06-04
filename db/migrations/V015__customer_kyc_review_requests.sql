-- API-backed staff KYC re-confirmation requests.
-- KYC remains synthetic-only and does not call real KYC providers or external financial APIs.

CREATE TABLE customer_kyc_review_requests (
  request_id TEXT PRIMARY KEY,
  business_type TEXT NOT NULL CHECK (business_type = 'CUSTOMER_KYC_REVIEW'),
  business_reference_id TEXT NOT NULL UNIQUE,
  target_customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  target_account_id TEXT,
  target_transaction_id TEXT,
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  reason_code TEXT NOT NULL,
  review_trigger TEXT NOT NULL,
  previous_kyc_status TEXT NOT NULL CHECK (previous_kyc_status IN ('PENDING', 'VERIFIED', 'REVIEW_REQUIRED', 'REJECTED')),
  requested_kyc_status TEXT NOT NULL CHECK (requested_kyc_status = 'REVIEW_REQUIRED'),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'REVIEW_REQUESTED')),
  approval_id TEXT UNIQUE REFERENCES operator_approvals(approval_id),
  idempotency_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (business_type, requested_by, idempotency_key)
);

CREATE INDEX idx_customer_kyc_review_requests_customer_status
  ON customer_kyc_review_requests(target_customer_id, status, created_at);

CREATE INDEX idx_customer_kyc_review_requests_approval
  ON customer_kyc_review_requests(approval_id);
