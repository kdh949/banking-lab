# Structured Error Contract Gap Report

Review date: 2026-06-02

## Current State

`docs/migration/structured-api-error-contract.md` defines the shared response shape and ten required error families. The Node reference has executable tests for error shape and selected policy/ledger failures in `tests/apiErrorContract.test.mjs`. The Spring target now has HTTP integration coverage in `services/core-banking/src/integrationTest/kotlin/lab/banking/core/api/StructuredApiErrorContractIntegrationTest.kt`.

The required response shape is proven for all ten required families at Spring HTTP level. Ledger families use real ledger endpoints; non-ledger families use the `api-error-parity` profile probe until staff/customer/workflow HTTP APIs are fully implemented.

## Required Error Family Coverage

| Code | Node evidence | Spring target evidence | Gap |
| --- | --- | --- | --- |
| `POLICY_REASON_REQUIRED` | Runtime staff search test returns structured error. | HTTP probe under `api-error-parity` profile. | Replace probe with real staff/customer route once auth APIs land. |
| `AUTHORIZATION_POLICY_VIOLATION` | Staff unmask unauthorized role returns 403 in Node; structured family is defined. | HTTP probe under `api-error-parity` profile. | Replace probe with Keycloak/resource-server denial test. |
| `MAKER_CHECKER_SELF_APPROVAL_REJECTED` | Staff, complaint, FDS tests assert structured self-approval error. | HTTP probe under `api-error-parity` profile. | Replace probe with real approval endpoint test. |
| `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` | Node structured error contract test covers withdrawal overdraw. | Spring withdrawal API overdraw test. | None for error shape; broader route parity remains separate. |
| `LEDGER_CLOSED_DAY_IMMUTABLE` | Node FDS/reconciliation and inference tests cover closed-day failure. | Spring daily closing plus post-on-closed-day test. | None for error shape; broader route parity remains separate. |
| `LEDGER_REVERSAL_POLICY_VIOLATION` | Node ledger core rejects duplicate reversal, but structured API coverage is indirect. | Spring duplicate reversal API test. | Add broader reversal policy cases before final parity review. |
| `REQUEST_VALIDATION_FAILED` | Contract defined. | HTTP probe under `api-error-parity` profile. | Replace probe with real malformed/missing payload route tests. |
| `RESOURCE_NOT_FOUND` | Contract defined. | HTTP probe and live missing ledger account smoke. | Add customer/case/resource route tests as APIs land. |
| `WORKFLOW_STATE_VIOLATION` | Complaint/FDS Node tests cover state controls but not a dedicated structured error test. | HTTP probe plus durable workflow integration state violation test. | Replace probe with real workflow route test. |
| `INTERNAL_RUNTIME_ERROR` | Contract defined. | HTTP probe under `api-error-parity` profile. | Keep probe profile-only; do not expose runtime error path in production. |

## Shape Gaps

Every target error response must include:

- `contractVersion`
- `code`
- `message`
- `statusCode`
- `domain`
- `invariant`
- `policy`
- `cause`
- `fix`
- `requestId`
- `correlationId`
- `route`
- `docs`
- `syntheticOnly`

Current Spring HTTP integration assertions verify these fields for every required family, including request ID propagation, correlation ID, route, docs, and `syntheticOnly`.

## Safety Gaps

- Add negative assertions that messages, causes, and fixes do not include unmasked account numbers, raw phone numbers, addresses, card-like identifiers, or real provider names.
- Add a contract rule that all documented financial/network identifiers remain synthetic.
- Ensure authorization failures do not reveal whether a customer/account/case exists unless the actor is allowed to know.

## Recommendation

Mark the structured error contract gate as passed for response-shape coverage. Keep Node retirement blocked until probe-backed families are replaced by real staff/customer/workflow/security endpoints and full parity review passes.
