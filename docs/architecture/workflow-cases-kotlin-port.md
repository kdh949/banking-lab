# Workflow Cases Kotlin Port

Date: 2026-06-02

## Scope

Agent B added Kotlin state-machine coverage for the workflow/case controls currently proven by:

- `tests/makerChecker.test.mjs`
- `tests/complaintWorkflow.test.mjs`
- `tests/fdsAmlReconciliation.test.mjs`

The Node runtime remains the executable oracle until Spring controllers, persistence repositories, and parity tests are wired end to end.

## Implemented Controls

- Maker-checker high-risk approval submission, rejection of self-approval, and command audit event capture.
- Complaint answer drafting with `COMPLAINT_ANSWER_SEND` approval before customer-visible answer release.
- Customer complaint confirmation after answer release.
- FDS held transfer release/block approval lifecycle.
- AML high-risk customer case investigation, comment, closure approval, and synthetic STR disposition flag.
- Reconciliation mismatch ownership and approved adjustment command handoff for an open business date.

## Structured Error Semantics

The Kotlin workflows throw `BankingLabDomainException` with stable contract codes:

- `POLICY_REASON_REQUIRED`
- `REQUEST_VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `MAKER_CHECKER_SELF_APPROVAL_REJECTED`
- `WORKFLOW_STATE_VIOLATION`

These map to `docs/migration/structured-api-error-contract.md` and preserve Node reference semantics for the assigned workflow slice.

## Persistence Readiness

`db/migrations/V007__workflow_case_lifecycle.sql` adds:

- `complaint_cases`
- `complaint_case_timeline`
- `fds_case_timeline`
- `aml_case_comments`
- `reconciliation_adjustment_requests`

FDS, AML, and reconciliation base tables remain owned by the existing V005 migration. Ledger posting for approved FDS release and reconciliation adjustment remains owned by the ledger service; this slice emits `InternalTransferCommand` and `AdjustmentCommand` handoff objects instead of posting directly.

## Evidence

Reference oracle command:

```bash
node --test tests/makerChecker.test.mjs tests/complaintWorkflow.test.mjs tests/fdsAmlReconciliation.test.mjs
```

Result with local listen permission: 10 passing, 0 failing.

Kotlin command attempted on the host:

```bash
./gradlew :services:core-banking:test
```

Result: not executed because this environment cannot locate a Java runtime.

Kotlin command verified through the documented Docker/JDK path:

```bash
docker run --rm -v /Users/donghyunkim/Documents/banking-lab:/workspace -w /workspace gradle:8.14.3-jdk21 ./gradlew :services:core-banking:test --no-daemon
```

Result: passed. The full `:services:core-banking:integrationTest` command also passed through Docker/JDK/Testcontainers and Flyway-applied V007.

## Remaining Integration Work

- Wire Spring controllers/routes to these state machines or persistence-backed services.
- Add repositories for V007 tables and existing V005 case tables.
- Attach approval execution callbacks so approved commands emit outbox events and execute ledger handoffs transactionally.
- Add Temporal workflow adapters when the Temporal worker slice starts.
