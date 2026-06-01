# Phase 4 Customer Web Architecture

## Customer Flow

```text
Customer Web
  -> mock login
  -> account list/detail
  -> transaction history from LedgerCore
  -> transfer command
       -> idempotency result check
       -> FDS hold check
       -> LedgerCore transfer or business failure
       -> result record
  -> complaint portal entry
```

## Runtime APIs

- `POST /api/customer/login`
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
