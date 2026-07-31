# Payment ledger-posted semantics

## Decision

`PaymentInstructionStatus.LEDGER_POSTED` means that core-banking accepted the synthetic bill-payment command and returned a `TX-*` ledger transaction reference. It does **not** mean that an external biller, clearing institution, payment network, or payout account completed settlement.

The payment instruction lifecycle is therefore:

```text
POSTING_REQUESTED -> LEDGER_POSTED
                  -> FAILED
                  -> CANCELED (before ledger posting only)
```

External clearing and settlement will be modeled as a separate aggregate with its own value date, batch, gross/fee/VAT/net position, payout reference, and reconciliation result. The payment instruction must not reuse `LEDGER_POSTED` as evidence of that future process.

## Compatibility boundary

- Canonical callback: `POST /api/payments/instructions/{instructionId}/ledger-postings`.
- Deprecated compatibility callback: `POST /api/payments/instructions/{instructionId}/settlements`.
- Canonical domain event: `PaymentInstructionLedgerPosted` with `status=LEDGER_POSTED` and `externalSettlementCompleted=false`.
- Historical `PaymentInstructionSettled` schema remains checked in for already-published event compatibility, but active publishers no longer select it.
- Flyway V006 converts persisted `SETTLED` instruction/attempt/history rows and unpublished legacy events. Published historical events remain unchanged and continue to validate against the legacy schema.

All paths remain synthetic-only and do not use a real payment network or external financial institution API.
