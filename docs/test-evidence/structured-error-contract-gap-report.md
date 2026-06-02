# Structured Error Contract Gap Report

Review date: 2026-06-02

## Current State

`docs/migration/structured-api-error-contract.md` defines the shared response shape and ten required error families. The Node reference has executable tests for error shape and selected policy/ledger failures in `tests/apiErrorContract.test.mjs`. The Spring target has structured error DTO/handler source and structural tests in `tests/springScaffold.test.mjs`.

That is not full contract parity yet. The target stack has one Spring HTTP integration test for duplicate reversal policy errors, but still needs coverage for the remaining required families, status codes, request IDs, correlation IDs, invariant or policy fields, and synthetic-only safety fields.

## Required Error Family Coverage

| Code | Node evidence | Spring target evidence | Gap |
| --- | --- | --- | --- |
| `POLICY_REASON_REQUIRED` | Runtime staff search test returns structured error. | Structural handler only. | Add Spring API test for reason-required staff/customer/account/transaction access. |
| `AUTHORIZATION_POLICY_VIOLATION` | Staff unmask unauthorized role returns 403 in Node; structured family is defined. | Not proven. | Add Spring/Keycloak or mock-token resource-server test for role/customer ownership denial. |
| `MAKER_CHECKER_SELF_APPROVAL_REJECTED` | Staff, complaint, FDS tests assert structured self-approval error. | Not proven. | Add target approval endpoint test with same maker/checker. |
| `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` | Node structured error contract test covers withdrawal overdraw. | Not proven at HTTP level. | Add Spring withdrawal API test. |
| `LEDGER_CLOSED_DAY_IMMUTABLE` | Node FDS/reconciliation and inference tests cover closed-day failure. | Not proven at HTTP level. | Add Spring daily closing plus post-on-closed-day test. |
| `LEDGER_REVERSAL_POLICY_VIOLATION` | Node ledger core rejects duplicate reversal, but structured API coverage is indirect. | Spring MockMvc/Testcontainers test covers duplicate reversal shape and `x-request-id` propagation. | Add broader reversal policy cases, including reversing a reversal and not-found originals. |
| `REQUEST_VALIDATION_FAILED` | Contract defined. | Not proven. | Add malformed/missing payload tests for Node and Spring. |
| `RESOURCE_NOT_FOUND` | Contract defined. | Not proven. | Add missing account/customer/case/transaction route tests. |
| `WORKFLOW_STATE_VIOLATION` | Complaint/FDS Node tests cover state controls but not a dedicated structured error test. | Not proven. | Add invalid workflow transition tests for complaint, FDS, AML, and reconciliation. |
| `INTERNAL_RUNTIME_ERROR` | Contract defined. | Not proven. | Add a controlled test-only unmapped error path or document why this is checked through handler unit tests only. |

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

Current Node `assertErrorShape` verifies these fields except it does not assert `route` type for every case; Spring structural tests prove source shape and the duplicate reversal integration test proves one runtime value path. Add target HTTP assertions for the remaining error families, generated fallback request IDs, and route values.

## Safety Gaps

- Add negative assertions that messages, causes, and fixes do not include unmasked account numbers, raw phone numbers, addresses, card-like identifiers, or real provider names.
- Add a contract rule that all documented financial/network identifiers remain synthetic.
- Ensure authorization failures do not reveal whether a customer/account/case exists unless the actor is allowed to know.

## Recommendation

Keep the structured error contract gate `in-progress`. It should move to complete only after all required error families have at least one target-stack HTTP integration test and the Node/Spring response shape is proven equivalent for request ID, correlation ID, route, docs, and synthetic-only safety.
