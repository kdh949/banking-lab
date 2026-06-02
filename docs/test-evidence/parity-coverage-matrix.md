# Parity Coverage Matrix

Review date: 2026-06-02

## Scope

This matrix reviews the current Node reference oracle against the Kotlin/Spring Boot and Next.js target migration gates. It does not mark parity complete; it records which reference scenarios are covered by mapped target suites and which target evidence is still missing.

Source of truth:

- `docs/migration/parity-scenarios.json`
- `tests/*.test.mjs`
- `docs/migration/node-retirement-gate.json`
- `docs/test-evidence/migration-foundation.md`

## Summary

| Area | Node reference scenarios | Target suite | Target status | Retirement impact |
| --- | ---: | --- | --- | --- |
| Ledger domain invariants | 5 | `services/core-banking/src/test/kotlin/lab/banking/core/ledger/domain/LedgerInvariantsTest.kt` | Pass for current slice | Keep Node oracle until every mapped suite is target-backed. |
| Ledger command service and isolation | 7 | `services/core-banking/src/integrationTest/kotlin/lab/banking/core/ledger/application/LedgerCommandServiceIntegrationTest.kt` | Pass for current slice | Keep Node oracle until retry policy and non-ledger suites are complete. |
| Audit and masking | 3 | `services/core-banking/src/test/kotlin/.../AuditMaskingParityTest.kt` | Planned | Blocks retirement. |
| Maker-checker | 2 | `services/core-banking/src/test/kotlin/lab/banking/core/approval/MakerCheckerParityTest.kt` | In progress | Blocks retirement until persistence/API execution parity is complete. |
| Screen manifest contract | 4 | `services/core-banking/src/test/kotlin/.../ScreenManifestContractTest.kt` | Planned | Blocks retirement for staff/customer screen controls. |
| Runtime API parity and errors | 4 | `services/core-banking/src/integrationTest/kotlin/lab/banking/core/ledger/api/LedgerRuntimeApiParityIntegrationTest.kt`, `services/core-banking/src/integrationTest/kotlin/lab/banking/core/api/StructuredApiErrorContractIntegrationTest.kt` | Partial | Blocks retirement for non-ledger route semantics. |
| Customer web | 5 | `apps/customer-web/e2e/customer-web-parity.spec.ts` | Shell parity pass | Blocks retirement until API-backed flows replace shell-only checks. |
| Staff terminal | 4 | `apps/staff-terminal/e2e/staff-terminal-parity.spec.ts` | Shell parity pass | Blocks retirement until staff controls are API/auth-backed. |
| Complaint workflow | 4 | `services/core-banking/src/test/kotlin/lab/banking/core/complaint/ComplaintWorkflowParityTest.kt` | In progress | Blocks retirement until workflow state and approval parity are durable and API-backed. |
| FDS, AML, reconciliation | 4 | `services/core-banking/src/test/kotlin/lab/banking/core/fds/FdsAmlReconciliationWorkflowParityTest.kt` | In progress | Blocks retirement until risk/reconciliation cases are target-stack backed and transactionally wired. |

Total mapped reference scenarios: 42.

## Control Coverage

| Control | Current evidence | Target-stack parity status | Gap |
| --- | --- | --- | --- |
| Ledger | Node tests plus Kotlin unit/integration evidence and live Spring `/health` smoke. | Partial. | Retry behavior after serialization conflicts needs a decision before final retirement. |
| Idempotency | Node tests and Kotlin ledger integration evidence. | Partial. | Needs API-level target parity for duplicate external commands outside ledger-only integration tests. |
| Audit | Node audit/runtime/staff tests. | Planned. | Needs Spring-backed audit event persistence and hash-chain verification tests. |
| Masking | Node audit/staff/customer tests. | Planned. | Needs target API and Next/Playwright assertions that PII is masked by default. |
| Maker-checker | Node maker-checker/staff/complaint/FDS/AML tests plus Kotlin `ApprovalStore` unit tests. | Partial. | Needs durable target approval state and API execution tests. |
| Workflow | Node complaint/FDS/AML/reconciliation state machines plus Kotlin state-machine unit tests and durable PostgreSQL workflow repository tests. | Partial. | Needs case-specific API wiring and final Temporal-deferral justification review. |
| Complaint | Node complaint workflow tests plus Kotlin complaint workflow unit tests. | Partial. | Needs target complaint API parity and durable customer-visible answer controls. |
| FDS/AML | Node FDS/AML tests plus Kotlin FDS/AML workflow unit tests. | Partial. | Needs target analytics/risk workflow parity and approval-controlled release/block/closure through APIs. |
| Reconciliation | Node ledger and FDS/AML/reconciliation tests, SQL migration evidence, and Kotlin adjustment handoff tests. | Partial. | Needs target EOD, mismatch ownership, and transactionally executed adjustment tests beyond handoff commands. |
| Manifest | Node manifest validation, expanded template tests, six Next shells, and Playwright shell parity. | Partial. | Needs API-backed interaction parity for required channel workflows. |
| Evidence | Evidence pack and migration foundation docs. | Partial. | Needs command logs for full target workflow, security scans, Playwright, and workflow/event tests. |
| Structured errors | Node API error contract tests plus Spring HTTP integration tests for all required error families. | Pass for response shape. | Probe-backed families need real API route replacement before final retirement. |

## Covered Reference Scenarios

The current parity map covers every Node oracle suite by count:

| Node suite | Scenario count | Main behaviors covered by the oracle |
| --- | ---: | --- |
| `tests/ledger.test.mjs` | 5 | Balanced postings, balance projection, idempotency, reversal, unbalanced transaction rejection. |
| `tests/ledgerCore.test.mjs` | 7 | Deposit, withdrawal, transfer, idempotency replay, reversal duplication guard, concurrent withdrawals, closed-day rejection. |
| `tests/audit.test.mjs` | 3 | Reason-required staff access, append-only audit hash chain, PII masking. |
| `tests/makerChecker.test.mjs` | 2 | Required approval data and maker/checker separation. |
| `tests/manifest.test.mjs` | 4 | App manifest coverage, high-risk approval metadata, PII inquiry policies, SQL control tables. |
| `tests/runtime.test.mjs` | 4 | Health, staff search audit reason, customer transfer idempotency, withdrawal/reversal API invariants. |
| `tests/customerWeb.test.mjs` | 5 | Login audit, shared transaction source, transfer retry, held/failed states, complaint entry shell. |
| `tests/staffTerminal.test.mjs` | 4 | Masked detail, privileged unmask, account/transaction inquiry reasons, customer change approval. |
| `tests/complaintWorkflow.test.mjs` | 4 | Shared case, SLA/timeline, answer approval, customer closure, complaint manifests. |
| `tests/fdsAmlReconciliation.test.mjs` | 4 | FDS release/block, AML closure, closed-day rejection, reconciliation adjustment. |

## Retirement Recommendation

Node retirement is not recommended. The reference runtime remains required until all 42 mapped scenarios have green target-stack parity evidence and the `retirement-review` gate has evidence that ledger, idempotency, audit, masking, maker-checker, workflow, reconciliation, and evidence behavior no longer depends on Node-only code.
