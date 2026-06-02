# Agent F QA Evidence Review Coordination Notes

Review date: 2026-06-02

## Coordinator-Owned Changes Requested

These items require owners outside the QA evidence slice because they touch implementation, build configuration, runtime services, or existing generated evidence scripts.

| Request | Owner area | Reason |
| --- | --- | --- |
| Add live Spring Boot smoke evidence for `/health` and one successful/failing ledger API call. | Backend/platform | QA can document the gap, but boot/runtime wiring is outside this slice. |
| Add target HTTP structured-error integration tests for every required error family. | Backend | Current Spring evidence is structural, not full runtime parity. |
| Add durable audit, masking, approval, and maker-checker PostgreSQL tests. | Backend/control-plane | Node still owns executable proof for these controls. |
| Add Kafka/Redpanda outbox publish, idempotent consumer, retry, and dead-letter tests. | Eventing | Outbox rows alone do not prove event backbone behavior. |
| Add Temporal or durable state-machine workflow recovery tests. | Workflow | Complaint/FDS/AML/reconciliation workflows still rely on Node oracle behavior. |
| Add Next.js/Playwright parity flows for all required channel surfaces. | Frontend | Current Next evidence covers customer-web scaffold only. |
| Add Keycloak/OAuth2/OIDC authorization enforcement tests. | Identity/security | Realm config without API enforcement is not enough for retirement. |
| Add Semgrep, Trivy, SBOM, SCA, DAST, and observability smoke outputs to evidence pack. | Security/platform | Required by PLAN Phase 7 before bank-grade evidence claims. |
| Teach the evidence pack to distinguish Node oracle pass from target parity pass. | Evidence tooling | Prevents a green Node pack from being misread as migration completion. |

## QA Slice Output

- `docs/test-evidence/parity-coverage-matrix.md`
- `docs/test-evidence/evidence-gap-report.md`
- `docs/test-evidence/structured-error-contract-gap-report.md`
- `docs/failure-drills/target-stack-gap-drill-additions.md`
- `docs/architecture/qa-evidence-node-retirement-recommendation.md`
- `scripts/check-qa-evidence-review.mjs`
- `tests/qaEvidenceCodexPlan.test.mjs`

## Retirement Position

Recommendation remains blocked. Do not update `docs/migration/node-retirement-gate.json` to ready until all retirement blockers in `docs/architecture/qa-evidence-node-retirement-recommendation.md` are resolved with command evidence.
