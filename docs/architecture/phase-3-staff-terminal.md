# Phase 3 Staff Terminal Architecture

## Staff Runtime Flow

```text
Staff terminal
  -> transaction code
  -> manifest metadata
  -> reason-required runtime API
  -> audit event
  -> masked response or approval request
```

## Implemented Transaction Codes

- `CST-001`: customer integrated search
- `CST-002`: customer detail
- `ACC-101`: account inquiry
- `LED-101`: ledger transaction history
- `CST-103`: customer information change request
- `APR-001`: maker-checker approval inbox
- `AUD-001`: audit event viewer

## Control Rules

- Customer, account, and transaction inquiry require a business reason.
- Staff PII responses are masked by default.
- Unmask requires a privileged role and reason, and returns a timeboxed exposure.
- Customer information change only creates an approval request.
- Manager approval applies the change and creates execution audit evidence.

## Current Boundary

The UI is still a lightweight static runtime shell. The important Phase 3 behavior is enforced in runtime APIs and tests, and the screens are declared in `screen-manifests/staff-terminal`.
