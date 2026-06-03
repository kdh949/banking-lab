-- Synthetic contact fields for staff masking and unmasking parity.
-- These columns are for generated lab data only; do not load real customer PII.

ALTER TABLE customers
  ADD COLUMN customer_phone TEXT,
  ADD COLUMN customer_address TEXT;
