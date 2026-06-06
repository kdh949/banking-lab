# Payment Service

## Goal

Implement synthetic billing, bill payment, autopay, payment instruction,
retry/cancel, payment status, and ledger settlement through core-banking command
contracts without using real payment networks.

## Current Code To Inspect

- `docs/implementation-coverage-matrix.md`
- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `contracts/events/**`
- `contracts/asyncapi/**`
- `infra/docker-compose/**`
- `screen-manifests/customer-web/**`
- `screen-manifests/staff-terminal/**`

## Target Folder Placement

Create `services/payment-service` for payment orchestration, synthetic billers,
autopay schedules, retries, and payment-network simulators. Ledger impact must
go through core-banking APIs/events; the payment service must not write ledger
tables directly.

## Backend Implementation Plan

- Implement bill catalog and synthetic biller simulator.
- Implement payment instruction creation, authorization, execution, retry,
  cancellation, and status inquiry.
- Implement autopay registration, pause, resume, and scheduled execution.
- Persist idempotency and payment status transitions.
- Post successful payments through core-banking ledger command contracts.

## Database / Migration Plan

Use payment bills, payment instructions, autopay agreements, payment attempts,
payment status history, idempotency records, and ledger reference fields. Use a
payment-service schema or migrations when the service is introduced.

## API / Event / Workflow Contracts

Define OpenAPI/AsyncAPI contracts for bill inquiry, payment instruction,
autopay, payment executed, payment failed, payment canceled, and retry/dead-letter
events. Include simulator-only metadata.

## Frontend / Screen Manifest Plan

Add customer bill payment/autopay screens and staff payment inquiry screens
through manifests. Admin parameter screens control synthetic biller settings.

## Security, Audit, Maker-Checker Controls

Customer payments require ownership and idempotency. Staff payment corrections
require reason and maker-checker. Autopay creation and cancellation are audited.

## Tests And Evidence

Test bill inquiry, payment success, retry, cancellation, duplicate idempotency
key, ledger posting, autopay execution, dead-letter behavior, and simulator-only
boundary. Add contract tests and integration tests.

## Acceptance Criteria

- Payment Service has durable state.
- Successful payments post through core-banking ledger.
- Retry/cancel behavior is idempotent and audited.
- No real payment network can be configured.

## Explicit Non-Goals

No real biller, card network, ACH, wire, open banking, bank API, or production
payment provider integration.
