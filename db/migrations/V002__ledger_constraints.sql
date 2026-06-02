-- Append-only and closed-day controls for the synthetic ledger.

CREATE OR REPLACE FUNCTION reject_ledger_source_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'finalized ledger source rows are append-only; use reversal or adjustment';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_no_update_delete
BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW
WHEN (OLD.status IN ('POSTED', 'REVERSED'))
EXECUTE FUNCTION reject_ledger_source_mutation();

CREATE TRIGGER ledger_postings_no_update_delete
BEFORE UPDATE OR DELETE ON ledger_postings
FOR EACH ROW
EXECUTE FUNCTION reject_ledger_source_mutation();

CREATE OR REPLACE FUNCTION reject_closed_day_ledger_posting()
RETURNS TRIGGER AS $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM daily_closings
    WHERE business_date = NEW.business_date
      AND status = 'CLOSED'
  ) THEN
    RAISE EXCEPTION 'business day is closed: %', NEW.business_date;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_closed_day_guard
BEFORE INSERT ON ledger_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_closed_day_ledger_posting();
