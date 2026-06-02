---
name: structured-error-contract-review
description: Review banking-lab API and domain failures for the structured error contract. Use when changing Node runtime APIs, Kotlin/Spring controllers, validation, authorization, ledger errors, workflow errors, or target parity tests.
---

# Structured Error Contract Review

## Scope

Use for any API/domain error behavior in the migration.

Read first:

1. `docs/migration/structured-api-error-contract.md`
2. `docs/migration/parity-scenarios.json`
3. `docs/migration/node-retirement-gate.json`
4. `tests/apiErrorContract.test.mjs` if present
5. Relevant controller/runtime tests

## Required Shape

Each domain failure must return:

- `error.contractVersion`
- `error.code`
- `error.message`
- `error.statusCode`
- `error.domain`
- `error.invariant` or `error.policy` when applicable
- `error.cause`
- `error.fix`
- `error.requestId`
- `error.correlationId`
- `error.route`
- `error.docs`
- `error.syntheticOnly: true`

## Required Families

Check at least these stable codes when relevant:

- `POLICY_REASON_REQUIRED`
- `AUTHORIZATION_POLICY_VIOLATION`
- `MAKER_CHECKER_SELF_APPROVAL_REJECTED`
- `LEDGER_INSUFFICIENT_AVAILABLE_BALANCE`
- `LEDGER_CLOSED_DAY_IMMUTABLE`
- `LEDGER_REVERSAL_POLICY_VIOLATION`
- `REQUEST_VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `WORKFLOW_STATE_VIOLATION`
- `INTERNAL_RUNTIME_ERROR`

## Safety Rules

- Do not leak real or unmasked PII in `message`, `cause`, or `fix`.
- Do not collapse ledger, maker-checker, masking, or workflow violations into generic runtime errors.
- Do not change an error code without updating parity tests and migration docs.
- New error families need contract tests and docs before retirement gate credit.

## Commands

```bash
npm test
npm run parity
```

When Kotlin APIs are involved:

```bash
./gradlew :services:core-banking:test
./gradlew :services:core-banking:integrationTest
```

## Output

Return findings first, then:

- Error families covered
- Routes or operations reviewed
- Contract tests run
- PII/masking risk
- Required coordinator-owned changes
