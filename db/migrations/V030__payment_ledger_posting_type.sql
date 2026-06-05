-- Synthetic payment-service settlement postings.

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
      'LOAN_REPAYMENT',
      'CARD_PURCHASE',
      'PAYMENT'
    )
  );
