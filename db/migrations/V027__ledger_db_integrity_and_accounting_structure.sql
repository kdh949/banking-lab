-- DB-level ledger integrity and accounting structure for the synthetic lab.
-- Source ledger rows remain append-only; this migration adds commit-time
-- balance enforcement, chart-of-accounts metadata, and date partition routing
-- evidence without replacing existing FK-compatible source tables.

ALTER TABLE accounts
  ADD COLUMN account_class TEXT NOT NULL DEFAULT 'LIABILITY',
  ADD COLUMN system_account_kind TEXT,
  ADD COLUMN synthetic_system_account BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE accounts
  ADD CONSTRAINT accounts_account_class_check
  CHECK (account_class IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'));

ALTER TABLE accounts
  ADD CONSTRAINT accounts_system_account_kind_check
  CHECK (
    system_account_kind IS NULL OR system_account_kind IN (
      'SUSPENSE',
      'CLEARING',
      'SETTLEMENT',
      'LOAN_ASSET',
      'INTEREST_EXPENSE',
      'FEE_INCOME',
      'LOAN_INTEREST_INCOME'
    )
  );

CREATE INDEX idx_accounts_class_kind
  ON accounts(account_class, system_account_kind)
  WHERE synthetic_system_account = TRUE;

INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
VALUES ('BANK', 'Synthetic Bank System Accounts', 'SYSTEM', 'LOW')
ON CONFLICT (customer_id) DO UPDATE SET
  customer_name = EXCLUDED.customer_name,
  customer_grade = EXCLUDED.customer_grade,
  risk_grade = EXCLUDED.risk_grade;

INSERT INTO accounts (
  account_id, customer_id, account_no, currency, status,
  account_class, system_account_kind, synthetic_system_account
)
VALUES
  ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE', 'LIABILITY', 'SUSPENSE', TRUE),
  ('BANK-LOAN-ASSET', 'BANK', 'LAB-000-000001', 'KRW', 'ACTIVE', 'ASSET', 'LOAN_ASSET', TRUE),
  ('BANK-CARD-CLEARING', 'BANK', 'LAB-000-000002', 'KRW', 'ACTIVE', 'LIABILITY', 'CLEARING', TRUE),
  ('BANK-SETTLEMENT', 'BANK', 'LAB-000-000003', 'KRW', 'ACTIVE', 'ASSET', 'SETTLEMENT', TRUE),
  ('BANK-INTEREST-EXPENSE', 'BANK', 'LAB-000-000004', 'KRW', 'ACTIVE', 'EXPENSE', 'INTEREST_EXPENSE', TRUE),
  ('BANK-FEE-INCOME', 'BANK', 'LAB-000-000005', 'KRW', 'ACTIVE', 'INCOME', 'FEE_INCOME', TRUE),
  ('BANK-LOAN-INTEREST-INCOME', 'BANK', 'LAB-000-000006', 'KRW', 'ACTIVE', 'INCOME', 'LOAN_INTEREST_INCOME', TRUE)
ON CONFLICT (account_id) DO UPDATE SET
  account_class = EXCLUDED.account_class,
  system_account_kind = EXCLUDED.system_account_kind,
  synthetic_system_account = TRUE,
  status = 'ACTIVE';

INSERT INTO account_balance_projections (account_id, currency, ledger_balance_minor, available_balance_minor)
SELECT account_id, currency, 0, 0
FROM accounts
WHERE synthetic_system_account = TRUE
ON CONFLICT (account_id, currency) DO NOTHING;

CREATE OR REPLACE FUNCTION validate_ledger_transaction_balance_at_commit()
RETURNS TRIGGER AS $$
DECLARE
  target_transaction_id TEXT;
  target_status TEXT;
  posting_count INTEGER;
  unbalanced_currency TEXT;
  unbalanced_total BIGINT;
BEGIN
  IF TG_TABLE_NAME = 'ledger_transactions' THEN
    target_transaction_id := NEW.ledger_transaction_id;
  ELSE
    target_transaction_id := COALESCE(NEW.ledger_transaction_id, OLD.ledger_transaction_id);
  END IF;

  SELECT status
  INTO target_status
  FROM ledger_transactions
  WHERE ledger_transaction_id = target_transaction_id;

  IF target_status IS NULL OR target_status NOT IN ('POSTED', 'REVERSED') THEN
    RETURN NULL;
  END IF;

  SELECT count(*)
  INTO posting_count
  FROM ledger_postings
  WHERE ledger_transaction_id = target_transaction_id;

  IF posting_count < 2 THEN
    RAISE EXCEPTION 'ledger transaction % must contain at least two postings', target_transaction_id
      USING ERRCODE = '23514';
  END IF;

  SELECT currency, signed_total
  INTO unbalanced_currency, unbalanced_total
  FROM (
    SELECT
      currency,
      SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS signed_total
    FROM ledger_postings
    WHERE ledger_transaction_id = target_transaction_id
    GROUP BY currency
  ) totals
  WHERE signed_total <> 0
  LIMIT 1;

  IF unbalanced_currency IS NOT NULL THEN
    RAISE EXCEPTION 'ledger transaction % is not balanced for %: %',
      target_transaction_id, unbalanced_currency, unbalanced_total
      USING ERRCODE = '23514';
  END IF;

  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_transactions_balance_at_commit
AFTER INSERT ON ledger_transactions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
WHEN (NEW.status IN ('POSTED', 'REVERSED'))
EXECUTE FUNCTION validate_ledger_transaction_balance_at_commit();

CREATE CONSTRAINT TRIGGER ledger_postings_balance_at_commit
AFTER INSERT ON ledger_postings
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_ledger_transaction_balance_at_commit();

CREATE TABLE ledger_transaction_partition_routes (
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  business_date DATE NOT NULL,
  partition_month DATE NOT NULL,
  routed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (ledger_transaction_id, business_date)
) PARTITION BY RANGE (business_date);

CREATE TABLE ledger_transaction_partition_routes_2026
  PARTITION OF ledger_transaction_partition_routes
  FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE ledger_transaction_partition_routes_default
  PARTITION OF ledger_transaction_partition_routes DEFAULT;

CREATE TABLE ledger_posting_partition_routes (
  ledger_posting_id TEXT NOT NULL REFERENCES ledger_postings(ledger_posting_id),
  ledger_transaction_id TEXT NOT NULL REFERENCES ledger_transactions(ledger_transaction_id),
  business_date DATE NOT NULL,
  partition_month DATE NOT NULL,
  routed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (ledger_posting_id, business_date)
) PARTITION BY RANGE (business_date);

CREATE TABLE ledger_posting_partition_routes_2026
  PARTITION OF ledger_posting_partition_routes
  FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE ledger_posting_partition_routes_default
  PARTITION OF ledger_posting_partition_routes DEFAULT;

CREATE OR REPLACE FUNCTION route_ledger_transaction_partition()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO ledger_transaction_partition_routes (
    ledger_transaction_id, business_date, partition_month
  )
  VALUES (
    NEW.ledger_transaction_id,
    NEW.business_date,
    date_trunc('month', NEW.business_date)::date
  )
  ON CONFLICT (ledger_transaction_id, business_date) DO UPDATE SET
    partition_month = EXCLUDED.partition_month,
    routed_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_partition_route
AFTER INSERT ON ledger_transactions
FOR EACH ROW
EXECUTE FUNCTION route_ledger_transaction_partition();

CREATE OR REPLACE FUNCTION route_ledger_posting_partition()
RETURNS TRIGGER AS $$
DECLARE
  transaction_business_date DATE;
BEGIN
  SELECT business_date
  INTO transaction_business_date
  FROM ledger_transactions
  WHERE ledger_transaction_id = NEW.ledger_transaction_id;

  INSERT INTO ledger_posting_partition_routes (
    ledger_posting_id, ledger_transaction_id, business_date, partition_month
  )
  VALUES (
    NEW.ledger_posting_id,
    NEW.ledger_transaction_id,
    transaction_business_date,
    date_trunc('month', transaction_business_date)::date
  )
  ON CONFLICT (ledger_posting_id, business_date) DO UPDATE SET
    ledger_transaction_id = EXCLUDED.ledger_transaction_id,
    partition_month = EXCLUDED.partition_month,
    routed_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_postings_partition_route
AFTER INSERT ON ledger_postings
FOR EACH ROW
EXECUTE FUNCTION route_ledger_posting_partition();

INSERT INTO ledger_transaction_partition_routes (
  ledger_transaction_id, business_date, partition_month
)
SELECT ledger_transaction_id, business_date, date_trunc('month', business_date)::date
FROM ledger_transactions
ON CONFLICT (ledger_transaction_id, business_date) DO NOTHING;

INSERT INTO ledger_posting_partition_routes (
  ledger_posting_id, ledger_transaction_id, business_date, partition_month
)
SELECT
  posting.ledger_posting_id,
  posting.ledger_transaction_id,
  transaction.business_date,
  date_trunc('month', transaction.business_date)::date
FROM ledger_postings posting
JOIN ledger_transactions transaction
  ON transaction.ledger_transaction_id = posting.ledger_transaction_id
ON CONFLICT (ledger_posting_id, business_date) DO NOTHING;
