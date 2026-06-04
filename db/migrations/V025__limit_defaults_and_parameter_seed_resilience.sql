-- Preserve compatibility for pre-V020 account limit inserts.
-- Older synthetic fixtures specify daily/single limits only; the trigger derives
-- a monthly transfer limit before NOT NULL and posting-time checks run.

CREATE OR REPLACE FUNCTION fill_account_limits_monthly_default()
RETURNS trigger AS $$
BEGIN
  IF NEW.monthly_transfer_limit_minor IS NULL THEN
    NEW.monthly_transfer_limit_minor := NEW.daily_transfer_limit_minor * 31;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fill_account_limits_monthly_default
BEFORE INSERT ON account_limits
FOR EACH ROW
EXECUTE FUNCTION fill_account_limits_monthly_default();
