# Structured API Error Contract

## Purpose

Kotlin/Spring Boot controllers and the current Node reference runtime must report domain failures with the same stable shape. Plain string errors are not enough for migration parity because they hide which invariant, policy, or workflow rule failed.

## Response Shape

```json
{
  "error": {
    "contractVersion": "2026-06-02",
    "code": "LEDGER_CLOSED_DAY_IMMUTABLE",
    "message": "business day is closed: 2026-01-31",
    "statusCode": 409,
    "domain": "ledger",
    "invariant": "closed day cannot be mutated directly",
    "policy": null,
    "cause": "A command attempted to post directly on a closed business date.",
    "fix": "Use an explicitly modeled reversal or adjustment transaction on an open business date.",
    "requestId": "REQ-00000001",
    "correlationId": "REQ-00000001",
    "route": "/api/ledger/deposits",
    "docs": "docs/migration/structured-api-error-contract.md",
    "syntheticOnly": true
  }
}
```

## Required Fields

- `contractVersion`: version of this error contract.
- `code`: stable machine-readable code.
- `message`: concise operator-facing message with synthetic identifiers only.
- `statusCode`: HTTP status code used by the response.
- `domain`: `ledger`, `audit`, `masking`, `maker-checker`, `workflow`, `manifest`, `validation`, `resource`, `auth`, or `runtime`.
- `invariant`: the broken domain invariant when applicable.
- `policy`: the broken authorization, masking, approval, or workflow policy when applicable.
- `cause`: short explanation of why the request failed.
- `fix`: actionable next step for a migration contributor or operator.
- `requestId`: request-scoped ID, propagated from `x-request-id` when supplied.
- `correlationId`: request or workflow correlation ID.
- `route`: route or API operation that emitted the error.
- `docs`: documentation path for the contract or domain.
- `syntheticOnly`: always `true` in this lab.

## Required Error Families

| Code | Domain | Trigger |
| --- | --- | --- |
| `POLICY_REASON_REQUIRED` | audit | Staff sensitive access or high-risk command lacks a business reason. |
| `AUTHORIZATION_POLICY_VIOLATION` | auth | Actor role or customer/case context is not allowed. |
| `STEP_UP_REQUIRED` | auth | Fresh MFA/WebAuthn step-up is required for a privileged staff/ops command. |
| `TRUSTED_DEVICE_REQUIRED` | auth | Customer transfer or staff session validation requires an active synthetic trusted-device binding. |
| `SESSION_REQUIRED` | auth | Session validation requires a session id and freshness claim. |
| `SESSION_EXPIRED` | auth | The modeled synthetic session TTL has elapsed. |
| `SESSION_REVOKED` | auth | The session id is present in the synthetic revoked-session registry. |
| `MAKER_CHECKER_SELF_APPROVAL_REJECTED` | maker-checker | Requester tries to approve their own high-risk operation. |
| `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE` | ledger | Debit command would overdraw projected available balance. |
| `LEDGER_CLOSED_DAY_IMMUTABLE` | ledger | Direct posting is attempted on a closed business date. |
| `LEDGER_REVERSAL_POLICY_VIOLATION` | ledger | Duplicate or invalid reversal request. |
| `REQUEST_VALIDATION_FAILED` | validation | Required payload/query value is missing or invalid. |
| `CUSTOMER_ONBOARDING_CHECK_FAILED` | validation | Synthetic onboarding check failed and blocks customer self-service progression. |
| `CUSTOMER_ACCOUNT_OPENING_ALREADY_PENDING` | account-opening | Customer already has a pending self-service account-opening intake for the same product and currency. |
| `CUSTOMER_AUTH_IDENTITY_NOT_ACTIVE` | auth | Customer route requires an active synthetic auth identity but the identity is locked or disabled. |
| `IDEMPOTENCY_KEY_CONFLICT` | idempotency | Externally retried command reused an idempotency key with a different command hash. |
| `RESOURCE_NOT_FOUND` | resource | Synthetic customer, account, case, transaction, or route is missing. |
| `WORKFLOW_STATE_VIOLATION` | workflow | Transition is invalid for the current state. |
| `INTERNAL_RUNTIME_ERROR` | runtime | Unmapped reference runtime exception. |

## Safety Rules

- Do not include real PII, real account numbers, real card numbers, or real payment identifiers.
- Do not leak unmasked customer data in `message`, `cause`, or `fix`.
- Do not downgrade ledger, idempotency, maker-checker, masking, or audit failures to generic success responses.
- Add a contract test before introducing a new domain error family.
