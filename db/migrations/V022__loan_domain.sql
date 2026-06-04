-- Synthetic loan domain slice.
-- Loan movements are posted through append-only balanced ledger transactions.

ALTER TABLE ledger_postings
  DROP CONSTRAINT ledger_postings_posting_type_check;

ALTER TABLE ledger_postings
  ADD CONSTRAINT ledger_postings_posting_type_check
  CHECK (
    posting_type IN (
      'PRINCIPAL',
      'FEE',
      'TAX',
      'HOLD',
      'REVERSAL',
      'OPENING',
      'ADJUSTMENT',
      'INTEREST',
      'LOAN_PRINCIPAL',
      'LOAN_INTEREST',
      'LOAN_REPAYMENT'
    )
  );

CREATE TABLE loan_products (
  product_id TEXT PRIMARY KEY,
  product_code TEXT NOT NULL UNIQUE,
  product_name TEXT NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'KRW',
  annual_rate_bps INTEGER NOT NULL CHECK (annual_rate_bps >= 0 AND annual_rate_bps <= 50000),
  term_months INTEGER NOT NULL CHECK (term_months > 0 AND term_months <= 360),
  minimum_amount_minor BIGINT NOT NULL CHECK (minimum_amount_minor > 0),
  maximum_amount_minor BIGINT NOT NULL CHECK (maximum_amount_minor >= minimum_amount_minor),
  approval_threshold_minor BIGINT NOT NULL CHECK (approval_threshold_minor >= 0),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
  synthetic_only BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE loan_applications (
  application_id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  deposit_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  product_id TEXT NOT NULL REFERENCES loan_products(product_id),
  requested_amount_minor BIGINT NOT NULL CHECK (requested_amount_minor > 0),
  requested_term_months INTEGER NOT NULL CHECK (requested_term_months > 0 AND requested_term_months <= 360),
  synthetic_monthly_income_minor BIGINT NOT NULL CHECK (synthetic_monthly_income_minor >= 0),
  synthetic_monthly_debt_minor BIGINT NOT NULL CHECK (synthetic_monthly_debt_minor >= 0),
  synthetic_credit_grade TEXT NOT NULL,
  synthetic_risk_grade TEXT NOT NULL,
  underwriting_score INTEGER NOT NULL CHECK (underwriting_score >= 0 AND underwriting_score <= 100),
  underwriting_decision TEXT NOT NULL CHECK (underwriting_decision IN ('APPROVE', 'REFER', 'DECLINE')),
  status TEXT NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'DECLINED', 'EXECUTED', 'REJECTED')),
  approval_id TEXT REFERENCES operator_approvals(approval_id),
  requested_by TEXT NOT NULL,
  requested_role TEXT NOT NULL,
  reason TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  executed_at TIMESTAMPTZ,
  CONSTRAINT loan_application_once_per_actor UNIQUE (requested_by, idempotency_key)
);

CREATE INDEX idx_loan_applications_customer ON loan_applications(customer_id, status);
CREATE INDEX idx_loan_applications_approval ON loan_applications(approval_id);

CREATE TABLE loans (
  loan_id TEXT PRIMARY KEY,
  application_id TEXT NOT NULL UNIQUE REFERENCES loan_applications(application_id),
  customer_id TEXT NOT NULL REFERENCES customers(customer_id),
  deposit_account_id TEXT NOT NULL REFERENCES accounts(account_id),
  product_id TEXT NOT NULL REFERENCES loan_products(product_id),
  principal_minor BIGINT NOT NULL CHECK (principal_minor > 0),
  outstanding_principal_minor BIGINT NOT NULL CHECK (outstanding_principal_minor >= 0),
  annual_rate_bps INTEGER NOT NULL CHECK (annual_rate_bps >= 0),
  term_months INTEGER NOT NULL CHECK (term_months > 0),
  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'OVERDUE', 'CLOSED')),
  disbursement_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  next_due_date DATE,
  overdue_days INTEGER NOT NULL DEFAULT 0 CHECK (overdue_days >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  disbursed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loans_customer ON loans(customer_id, status);

CREATE TABLE loan_repayment_schedule (
  schedule_id TEXT PRIMARY KEY,
  loan_id TEXT NOT NULL REFERENCES loans(loan_id),
  installment_no INTEGER NOT NULL CHECK (installment_no > 0),
  due_date DATE NOT NULL,
  principal_minor BIGINT NOT NULL CHECK (principal_minor >= 0),
  interest_minor BIGINT NOT NULL CHECK (interest_minor >= 0),
  total_minor BIGINT NOT NULL CHECK (total_minor = principal_minor + interest_minor),
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'PREPAID', 'SUPERSEDED')),
  ledger_transaction_id TEXT REFERENCES ledger_transactions(ledger_transaction_id),
  paid_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loan_schedule_due ON loan_repayment_schedule(loan_id, status, due_date);

CREATE TABLE loan_interest_accruals (
  accrual_id TEXT PRIMARY KEY,
  loan_id TEXT NOT NULL REFERENCES loans(loan_id),
  accrual_date DATE NOT NULL,
  outstanding_principal_minor BIGINT NOT NULL CHECK (outstanding_principal_minor >= 0),
  annual_rate_bps INTEGER NOT NULL CHECK (annual_rate_bps >= 0),
  interest_minor BIGINT NOT NULL CHECK (interest_minor >= 0),
  overdue_days INTEGER NOT NULL DEFAULT 0 CHECK (overdue_days >= 0),
  status TEXT NOT NULL CHECK (status IN ('CALCULATED', 'WAIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT loan_accrual_once_per_date UNIQUE (loan_id, accrual_date)
);

CREATE TABLE loan_payments (
  payment_id TEXT PRIMARY KEY,
  loan_id TEXT NOT NULL REFERENCES loans(loan_id),
  payment_type TEXT NOT NULL CHECK (payment_type IN ('SCHEDULED', 'PREPAYMENT')),
  principal_minor BIGINT NOT NULL CHECK (principal_minor >= 0),
  interest_minor BIGINT NOT NULL CHECK (interest_minor >= 0),
  total_minor BIGINT NOT NULL CHECK (total_minor = principal_minor + interest_minor AND total_minor > 0),
  business_date DATE NOT NULL,
  idempotency_key TEXT NOT NULL,
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  requested_by TEXT NOT NULL,
  requested_channel TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT loan_payment_once UNIQUE (loan_id, idempotency_key)
);

INSERT INTO loan_products (
  product_id, product_code, product_name, currency, annual_rate_bps, term_months,
  minimum_amount_minor, maximum_amount_minor, approval_threshold_minor, status, synthetic_only
)
VALUES (
  'LOAN-PROD-SYN-PERSONAL-001',
  'SYN-PERSONAL-FIXED',
  'Synthetic Personal Fixed Loan',
  'KRW',
  720,
  12,
  100000,
  50000000,
  100000,
  'ACTIVE',
  TRUE
)
ON CONFLICT (product_id) DO NOTHING;
