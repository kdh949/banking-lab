# Implementation Coverage Matrix

Review date: 2026-06-04

Scope: target-stack Banking Lab implementation only. The legacy Node runtime is treated as a reference oracle and is not counted as target-path implementation coverage.

Status values are limited to `complete`, `api-backed`, `manifest-only`, `partial`, `missing`, and `not-applicable`.

| Area | Feature/Screen | Manifest | Next UI | API Client | Spring API | PostgreSQL | Keycloak/AuthZ | Audit | Maker-checker | E2E/Integration Test | Evidence | Status |
| ---- | -------------- | -------- | ------- | ---------- | ---------- | ---------- | -------------- | ----- | ------------- | -------------------- | -------- | ------ |
| customer-web | Account overview/detail/history: `CWB-101`, `CWB-102`, `CWB-103` | yes | yes | yes | yes | yes | customer token path when configured | self-service account audit | not-applicable | Playwright conditional API smoke, Spring integration | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| customer-web | Internal transfer and transfer result/status: `CWB-201`, `CWB-202`, `CWB-203` | yes | yes | yes | yes | ledger postings and transfer result read model | customer ownership policy | command audit and outbox | not-applicable | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/phase-4-customer-web.md` | api-backed |
| customer-web | Complaint entry/list/answer confirmation: `CWB-301`, `CWB-302`, `CWB-303` | yes | yes | yes | yes | complaint tables | customer ownership policy | timeline and command audit | answer approval happens on staff path | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/phase-5-complaint-workflow.md` | api-backed |
| customer-web | Security and access history: `CWB-401` | yes | manifest shell only | no dedicated client method found | no dedicated route found | audit data exists, customer read model missing | declared role only | audit source exists | not-applicable | manifest validation only | this matrix | manifest-only |
| staff-terminal | Customer/account/transaction inquiry: `CST-001`, `CST-002`, `CST-003`, `ACC-101`, `LED-101` | yes | yes | partial shared client coverage | yes | yes | staff role policy | reason-required audit | not-applicable | Node oracle, Spring integration, Playwright shell | `docs/test-evidence/staff-terminal-renderer-e2e.md` | api-backed |
| staff-terminal | Declared account/ledger detail screens: `ACC-102`, `LED-102` | yes | manifest renderer | no dedicated client method found | no dedicated detail route found | source tables exist | declared role only | declared reason/masking | not-applicable | manifest validation only | this matrix | manifest-only |
| staff-terminal | Customer profile changes: `CST-101`, `CST-102`, `CST-103` | yes | yes | yes | yes | customer profile and approval state | staff/checker roles | request/approve/execute audit | yes | Node oracle, Spring integration, Playwright conditional smoke | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Privileged PII unmask: `CST-104` | yes | yes | yes | yes | audit event state | manager/auditor/compliance policy | time-boxed unmask audit | declared as high risk | Node oracle, Spring integration, Playwright conditional smoke | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Approval inbox and audit log: `APR-001`, `AUD-001` | yes | yes | yes | yes | approvals and audit events | manager/auditor roles | hash-chain visible | approval execution path | conditional Playwright tests added; live API not run locally | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Complaint workflow screens: `CMP-201`, `CMP-202` | yes | yes | yes | yes | complaint tables and approvals | complaint/checker roles | workflow audit | yes | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/phase-5-complaint-workflow.md` | api-backed |
| staff-terminal | FDS release/block and AML closure: `SFD-101`, `SFD-102`, `SFD-103`, `SAM-101`, `SAM-102` | yes | yes | yes | yes | FDS/AML case tables and ledger release posting | reviewer/checker roles | workflow audit | yes | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/fds-aml-reconciliation.md` | api-backed |
| staff-terminal | Reconciliation inquiry/adjustment: `REC-101`, `REC-102` | yes | yes | yes | yes | reconciliation and adjustment request tables | ops/checker roles | workflow audit | yes | Node oracle, Spring integration, conditional Playwright | `docs/reconciliation-reports/phase-6-eod-reconciliation.md` | api-backed |
| staff-terminal | Account hold request/release: `ACC-103`, `ACC-104` | yes | yes | yes | yes | `account_hold_requests` plus account/projection state | manager/call-center/ops route and service policy | request/approve/execute audit | yes | Spring integration test added; integration runtime pending Docker/CI, conditional Playwright smoke added | this matrix and `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Transfer limit inquiry/change: `LIM-101`, `LIM-102` | yes | yes | yes | yes | `account_limits` plus `account_limit_change_requests` | branch/manager route and service policy | `LIMIT_VIEW`, request/approve/execute audit | yes | Spring integration test added; conditional Playwright smoke added; local integration runtime depends on Docker/CI | this matrix and `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Fee inquiry/waiver: `FEE-101`, `FEE-102` | yes | manifest renderer | no dedicated client method found | no dedicated route found | fee policy/request tables missing | declared role only | declared | declared for waiver | manifest validation only | target goal Phase B/C | manifest-only |
| staff-terminal | KYC re-confirmation request: `KYC-101` | yes | yes | yes | yes | `customer_kyc_profiles` plus `customer_kyc_review_requests` | branch/manager/compliance route and service policy | request/approve/execute audit with `realKycProviderCalled=false` | yes | Spring integration test added; conditional Playwright smoke added; local integration runtime depends on Docker/CI | this matrix and `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| staff-terminal | Transaction correction request and approved reversal/adjustment | no dedicated manifest found | no | no | generic ledger adjustment exists only | ledger supports reversal/adjustment | no dedicated workflow policy | ledger audit only | no dedicated correction approval | ledger tests only | target goal Phase B | missing |
| complaint-portal | Complaint intake/status/answer/closure: `CMP-101`, `CMP-102`, `CMP-104`, `CMP-105`, `CMP-108` | yes | yes | yes | yes | complaint tables | customer ownership policy | timeline/audit | staff approval before answer | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/phase-5-complaint-workflow.md` | api-backed |
| complaint-portal | Additional materials, reopen, type guide: `CMP-103`, `CMP-106`, `CMP-107` | yes | manifest shell | partial/no dedicated client methods | routes not found for all actions | partial complaint state only | declared/customer role | declared | not-applicable | manifest validation only | target goal later phase | manifest-only |
| ops-console | Daily closing monitor: `OPS-101` | yes | yes | partial | close command exists; GET monitor route not found | business date and ledger tables | ops role policy | ledger audit | not-applicable | ledger and reconciliation tests | `docs/test-evidence/phase-2-ledger-core.md` | partial |
| ops-console | Reconciliation item review and adjustment: `OPS-201`, `OPS-202` | yes | yes | yes | yes | reconciliation adjustment request and ledger posting | ops/checker roles | workflow audit | yes | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/fds-aml-reconciliation.md` | api-backed |
| ops-console | Reconciliation parameters: `OPS-301` | yes | manifest shell | no dedicated client method found | no route found | parameter table missing | declared role only | declared | declared | manifest validation only | target goal Phase G/F follow-up | manifest-only |
| audit-console | Hash-chain/access log: `AUD-101`, `AUD-102` | yes | yes | yes | yes for `/api/audit/events` | audit event table | auditor/compliance policy | hash-chain validation | not-applicable | Spring integration, conditional Playwright | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| audit-console | Audit retention parameters: `AUD-201` | yes | manifest shell | no dedicated client method found | no route found | parameter table missing | declared role only | declared | declared | manifest validation only | target goal later phase | manifest-only |
| fds-aml-console | FDS/AML case dashboards and decisions: `FDS-101`, `FDS-201`, `FDS-202`, `AML-101`, `AML-201` | yes | yes | yes | yes | FDS/AML and approval tables | reviewer/checker roles | workflow audit | yes | Node oracle, Spring integration, conditional Playwright | `docs/test-evidence/fds-aml-reconciliation.md` | api-backed |
| fds-aml-console | FDS rule parameters: `FDS-301` | yes | manifest shell | no dedicated client method found | no route found | parameter table missing | declared role only | declared | declared | manifest validation only | target goal Phase D/F | manifest-only |
| admin-console | Platform control dashboard: `ADM-101` | yes | yes | yes | yes | reads configured target-state evidence | compliance/passkey role policy | admin access audit path partial | not-applicable | Spring integration, conditional Playwright | `docs/test-evidence/api-backed-channel-smoke.md` | api-backed |
| admin-console | Security policy and menu/role parameters: `ADM-201`, `ADM-301` | yes | manifest shell | no dedicated client methods found | no route found | parameter tables missing | declared role only | declared | declared | manifest validation only | target goal later phase | manifest-only |
| core-banking | Ledger deposit/withdrawal/transfer/reversal/adjustment/closing | not-applicable | not-applicable | partial channel clients | yes | yes | API role filter for ledger/staff/customer paths | ledger audit/outbox | adjustment approval where routed through ops | JUnit unit passes; integration blocked locally by Docker absence | `docs/test-evidence/phase-2-ledger-core.md` | api-backed |
| core-banking | Structured errors, audit hash chain, approval API, outbox, Temporal references | not-applicable | not-applicable | yes for active channel paths | yes | yes | yes | yes | yes for high-risk workflows | Node/JUnit/conditional E2E; integration needs Docker | `docs/test-evidence/evidence-gap-report.md` | api-backed |
| product-ledger | Deposit product catalog, rates, fee policy, interest accrual/posting batches | no | no | no | no | no product/fee/interest migrations found | no | no | no | none | target goal Phase C | missing |
| analytics | Python AML/FDS scoring, DuckDB mart, exports, console/evidence linkage | not-applicable | partial FDS/AML console case view only | no analytics client | no Spring adapter found | no DuckDB mart/sample-data export | not-applicable | generated artifact missing | not-applicable | `test_scoring.py` covers deterministic rules only | target goal Phase D | partial |
| formal | Executable TLC model checking gate | not-applicable | not-applicable | not-applicable | not-applicable | not-applicable | not-applicable | not-applicable | not-applicable | static TLA artifact check only; TLC skipped locally | `docs/test-evidence/generated/formal-ledger-model.json` | partial |
| platform | Kubernetes/Helm/Argo CD skeleton and validation scripts | partial files exist | not-applicable | not-applicable | not-applicable | partial manifests | not-applicable | not-applicable | not-applicable | `docker compose config` passes; `k8s:validate` and `helm:template` scripts missing | `infra/k8s`, `infra/helm`, `infra/argocd` | partial |
| operations-evidence | Load test and backup/restore drill | no | no | no | no | no executable drill found | not-applicable | no generated evidence | not-applicable | scripts missing | target goal Phase G | missing |

## Baseline Commands

Commands run on 2026-06-04 for this review:

- `npm ci`: pass.
- `npm test`: pass, 131 tests.
- `npm run validate:manifests`: pass, 67 manifests.
- `npm run test:screen-engine`: pass, 10 tests.
- `npm run packages:typecheck`: pass.
- `npm run scripts:typecheck`: pass.
- `npm run next:staff-terminal:typecheck`: pass.
- `npm run next:staff-terminal:build`: pass.
- `npm run test:e2e -- apps/staff-terminal/e2e/staff-terminal-parity.spec.ts`: pass after Phase B KYC-101 update, 5 passed and 10 skipped because API/Keycloak variables were not configured.
- `npm run test:e2e`: pass after Phase B KYC-101 update, 17 passed and 37 skipped because API/Keycloak variables were not configured. A first parallel attempt in a previous PR failed with port 3001 already in use and is not counted as product failure.
- `docker compose config`: pass.
- `docker compose --profile platform config`: pass.
- `npm run test:core-banking:unit -- --rerun-tasks`: pass after sandbox escalation.
- `scripts/run-core-banking-tests.sh :services:core-banking:compileKotlin :services:core-banking:compileIntegrationTestKotlin`: pass after sandbox escalation.
- `npm run test:core-banking:integration -- --tests lab.banking.core.staff.StaffAccessApiParityIntegrationTest`: failed at Testcontainers initialization because Docker provider discovery failed locally; integration source compiled successfully and CI must provide the runtime result.
- `npm run test:core-banking`: integration phase failed because Testcontainers could not find a Docker provider in this environment.
- `npm run formal:ledger`: static artifact check passed, TLC skipped.
- `npm run security:evidence`: npm audit passed after escalation; Semgrep, Trivy, and SBOM generation could not run because Docker was unavailable; DAST skipped because `BANKING_LAB_DAST_URL` was not set.
- `npm run evidence:refresh-check`: pass.
- `npm run retirement:audit`: pass.
- `npm run retirement:final-review:verify`: pass.
- `npm run node:retirement-gate`: pass.
- `npm run goal:completion-audit -- --require-complete`: pass for the previous Node retirement scope, not for the new missing-features goal in `docs/codex/implementation_missing_features_goals.md`.

## Immediate Implementation Plan

1. Phase B PRs should continue one staff command at a time: fee waiver, then transaction correction. Account hold/release, transfer limit change, and KYC review now have migration, Spring service/controller, api-client method, manifest wiring, staff-terminal panel, integration test source, and conditional Playwright smoke.
2. Phase C should add product/fee/interest tables only after fee waiver request semantics are in place, so fee posting and fee waiver policy share one approval model.
3. Phase D should expand the current Python scoring rule into DuckDB feature generation and generated evidence before trying to make the Spring/FDS console depend on a live Python service.
4. Phase E should replace the current static-only formal check with TLC or an explicit Docker/TLC runner that fails closed in CI.
5. Phase F/G should add validation scripts before adding more Kubernetes, load, or backup artifacts, so structural claims remain executable.
