# Structured Error Contract Gap Report

Review date: 2026-06-03

## Current State

`docs/migration/structured-api-error-contract.md` defines the shared response shape and ten required error families. The Node reference has executable tests for error shape and selected policy/ledger failures in `tests/apiErrorContract.test.mjs`. The Spring target now has HTTP integration coverage in `services/core-banking/src/integrationTest/kotlin/lab/banking/core/api/StructuredApiErrorContractIntegrationTest.kt`.

The required response shape is proven for all ten required families at Spring HTTP level. Nine required families now have real Spring route coverage: ledger endpoints, staff reason/masking routes, approval routes, customer/staff complaint routes, customer transfer routes, and workflow state routes. `INTERNAL_RUNTIME_ERROR` remains covered only through the `api-error-parity` profile-only probe because exposing an intentional runtime-error endpoint in production-like routes would be unsafe.

## Required Error Family Coverage

| Code | Node evidence | Spring target evidence | Gap |
| --- | --- | --- | --- |
| `POLICY_REASON_REQUIRED` | Runtime staff search test returns structured error. | Real staff customer detail route in `StructuredApiErrorContractIntegrationTest`, plus real approval endpoint validation in `ApprovalApiParityIntegrationTest`. | None for error shape; operational failure drills remain separate. |
| `AUTHORIZATION_POLICY_VIOLATION` | Staff unmask unauthorized role returns 403 in Node; structured family is defined. | Real staff unmask route in `StructuredApiErrorContractIntegrationTest`, plus `SecurityAuthorizationIntegrationTest`, including CORS-readable auth denial for browser clients, `JwksAuthorizationIntegrationTest`, `LiveKeycloakRealmIntegrationTest`, `KeycloakRealmPolicyTest`, customer-web Playwright coverage for missing/invalid Bearer token, signed JWT/JWKS validation, disabled simulator fallback, route role denial, customer ownership denial, actor-spoof denial, live Keycloak token propagation, TOTP and WebAuthn required-action blocking, synthetic WebAuthn policy and passkey recovery role segregation, staff-terminal WebAuthn browser completion with a virtual authenticator, customer-web browser login propagation for all current API-backed customer paths, staff-terminal browser login propagation for masked lookup, privileged unmask, and branch-maker/manager-checker customer-change approval, complaint-portal browser login propagation for answer approval plus duplicate answer workflow failure-state, ops-console browser login propagation for reconciliation adjustment plus adjusted-item workflow failure-state, audit-console browser login propagation for hash-chain read-model evidence, and FDS/AML-console browser login propagation for risk reviewer/checker command and failure-state paths. | Final retirement still needs non-synthetic passkey operations; error shape has real route coverage. |
| `MAKER_CHECKER_SELF_APPROVAL_REJECTED` | Staff, complaint, FDS tests assert structured self-approval error. | Real approval endpoint self-approval route in `StructuredApiErrorContractIntegrationTest`, `ApprovalApiParityIntegrationTest`, and browser staff customer-change self-approval rendering. | None for approval endpoint error shape; broader route parity remains separate. |
| `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` | Node structured error contract and customer transfer tests cover overdraw failures. | Spring withdrawal API overdraw test plus real customer transfer route rejection in `CustomerTransferApiParityIntegrationTest` and browser smoke for `/api/customer/transfers`. | None for error shape; broader route parity remains separate. |
| `LEDGER_CLOSED_DAY_IMMUTABLE` | Node FDS/reconciliation and inference tests cover closed-day failure. | Spring daily closing plus post-on-closed-day test. | None for error shape; broader route parity remains separate. |
| `LEDGER_REVERSAL_POLICY_VIOLATION` | Node ledger core rejects duplicate reversal, but structured API coverage is indirect. | Spring duplicate reversal API test. | Add broader reversal policy cases before final parity review. |
| `REQUEST_VALIDATION_FAILED` | Contract defined. | Real complaint answer-draft route with missing body in `StructuredApiErrorContractIntegrationTest`, plus customer transfer and complaint API validation tests. | None for error shape; broader validation matrix remains separate. |
| `RESOURCE_NOT_FOUND` | Contract defined. | Real staff customer detail route in `StructuredApiErrorContractIntegrationTest`, live missing ledger account smoke, and Temporal workflow reference not-found tests. | None for error shape; broader resource matrix remains separate. |
| `WORKFLOW_STATE_VIOLATION` | Complaint/FDS/AML/reconciliation Node tests cover state controls. | Real complaint answer-draft route rejection in `StructuredApiErrorContractIntegrationTest` and `ComplaintCaseApiParityIntegrationTest`, real FDS released-case route rejection in `FdsCaseApiParityIntegrationTest`, real AML closed-case route rejection in `AmlCaseApiParityIntegrationTest`, real reconciliation adjusted-item route rejection in `ReconciliationOpsApiParityIntegrationTest`, durable workflow integration state violation test, and browser smokes for `CMP-SYN-FAIL-001`, `FDS-SYN-FAIL-001`, `AML-SYN-FAIL-001`, and `REC-SYN-FAIL-001`. | None for error shape; operational retry/failure visibility remains separate. |
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

Mark the structured error contract gate as passed for response-shape coverage. Keep Node retirement blocked for the remaining retirement gates: non-synthetic passkey operations and final review.
