# External settlement import and batch foundation

## Decision

Payment Service now accepts an **independent synthetic external clearing CSV** and preserves its provenance before any comparison with internal payment or ledger data.

The import path deliberately does not generate an external file from `payment_instructions`, `ledger_transactions`, or internal reconciliation output. The caller supplies the file content, and the service persists:

- original file name
- institution code
- exact UTF-8 byte size
- SHA-256 digest
- received timestamp
- every original CSV row and line number
- external reference, payment instruction reference, biller, amount, dates, and external status

A repeated SHA-256 digest is rejected even when the caller changes the idempotency key. Replaying the original idempotency key returns the same import.

## Canonical CSV

```text
external_reference,payment_instruction_id,biller_id,amount_minor,currency,business_date,value_date,status
```

Allowed external statuses are `ACCEPTED`, `REJECTED`, and `RETURNED`. Only `ACCEPTED` rows are eligible for batch positions. Rejected and returned rows remain in staging for the next three-way reconciliation slice.

## Settlement batch arithmetic

Accepted lines are grouped by:

```text
biller_id + currency + business_date + value_date
```

For each group:

```text
gross_amount_minor
- fee_amount_minor        (gross × fee_rate_bps, half-up to minor units)
- vat_amount_minor        (fee × vat_rate_bps, half-up to minor units)
+ adjustment_amount_minor (zero in this foundation slice)
= net_amount_minor
```

The database repeats this arithmetic as a check constraint. `INCLUDED_IN_BATCH` means only that the external rows were included in a durable synthetic position. It does not mean payout was requested, money moved through a real payment network, or external settlement finality was reached.

## API and authorization boundary

```text
POST /api/payments/settlement/imports
GET  /api/payments/settlement/imports/{importId}
POST /api/payments/settlement/batch-runs
GET  /api/payments/settlement/batch-runs/{batchRunId}
```

Import and batch commands require `OPS_OPERATOR` or `OPS_MANAGER`. Read paths also allow `AUDITOR` and `COMPLIANCE_MANAGER`. Command `requestedBy` is bound to the authenticated token subject.

## Verification

`PaymentSettlementIntegrationTest` uses PostgreSQL Testcontainers and proves:

- unauthenticated and customer-role commands are denied
- spoofed command actors are rejected
- the same idempotency key replays one import and one batch run
- the same file SHA-256 cannot be re-imported under a different key
- raw staging rows are preserved while internal payment rows are not synthesized
- rejected rows remain staged but are excluded from payable positions
- utility gross `150000` becomes fee `1500`, VAT `150`, and net `148350`
- telco gross `20000` becomes fee `200`, VAT `20`, and net `19780`

`paymentSettlementFoundation.test.mjs`, OpenAPI source diffing, API-client exemption checks, and the V007 arithmetic constraint provide repository-level regression coverage.

## Deliberate boundary

This slice stops before:

- payment/ledger/external three-way reconciliation
- exception aging and SLA ownership
- maker-checker settlement adjustments
- payout request and final settlement transitions
- settlement ledger postings

Those controls will build on the independent import, raw-line provenance, and deterministic batch positions introduced here.
