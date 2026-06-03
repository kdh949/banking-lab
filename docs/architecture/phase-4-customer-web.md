# Phase 4 Customer Web Architecture

## Customer Flow

```text
Customer Web
  -> Keycloak/OIDC login propagation
  -> account list/detail
       -> masked customer self-service account detail
       -> ACCOUNT_VIEW audit event
  -> transaction history from LedgerCore
  -> transfer command
       -> idempotency result check
       -> FDS hold check
       -> LedgerCore transfer or business failure
       -> result record
  -> complaint portal entry
```

## Runtime APIs

- `GET /api/customer/accounts`
- `GET /api/customer/accounts/{accountId}/detail`
- `GET /api/customer/transactions`
- `POST /api/customer/transfers`
- `GET /api/customer/transfers`

## Transfer Result States

- `POSTED`: double-entry transfer posted to the ledger.
- `HELD`: FDS simulation held the transfer; no ledger posting occurred.
- `FAILED`: validation failed; no ledger posting occurred.

## Shared Source of Truth

Customer history and staff history both read from `LedgerCore` transactions and postings. Tests assert the same transaction ID appears through both channels after a customer transfer.

## Legacy Reference Boundary

The Node reference still exposes `POST /api/customer/login` for oracle tests and generated Phase 4 evidence. The target customer-web path uses Keycloak/OIDC token propagation and Spring customer APIs; new target code must not add a mock-login backend.
