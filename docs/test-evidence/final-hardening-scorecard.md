# Final Hardening Scorecard

Review date: 2026-06-10

Scope: synthetic banking lab portfolio readiness. This scorecard summarizes the current target-stack evidence and known limits. It does not claim production readiness, real financial-network readiness, or suitability for real customer money or real personal data.

Synthetic boundary: no real customer money, real PII, real KYC/AML provider, card network, payment network, regulator filing, or external financial-institution API is integrated.

Status vocabulary:

- `strong`: current synthetic-lab implementation has direct code, tests, and evidence for the stated scope.
- `partial`: meaningful implementation exists, but live breadth, UI exposure, or operational depth remains limited.
- `blocked`: the repository-side implementation can be checked locally, but external infrastructure prevents a requested hosted proof.
- `missing`: the PLAN-required feature is not implemented as a target-stack workflow yet.

| Area | Score | Status | Implemented evidence | Remaining limitation | Next step |
| --- | ---: | --- | --- | --- | --- |
| Ledger integrity | 9/10 | strong | PostgreSQL/Flyway ledger slice, DB-level deferred balance triggers, typed system accounts, projection equality, HA-shaped two-instance drill, and formal model evidence: `docs/test-evidence/hardening-h3-ledger-db-integrity.md`, `docs/test-evidence/hardening-h4-ha-dr-proof.md`, `docs/test-evidence/formal-ledger-verification.md` | Broader high-contention retry/operator-visible failure policy is still needed for future financial command paths. | Keep running `npm run ledger:integrity-check`, `npm run formal:ledger`, and targeted Spring integration tests before ledger changes. |
| Idempotency/reversal/adjustment | 9/10 | strong | Ledger command tests cover idempotent replay, reversal, adjustment, closed-date rejection, and balanced postings; ops reconciliation adjustment is approval-backed: `docs/test-evidence/phase-2-ledger-core.md`, `docs/test-evidence/fds-aml-reconciliation.md` | Future command families must inherit the same idempotency and serialization retry discipline. | Add idempotency/reversal checks with every new money-moving command. |
| Staff integrated terminal | 6/10 | partial | Current `staff-terminal` is the iWorks integrated shell with `/api/terminal-status`; staff controls remain Spring-backed and tested separately: `docs/implementation-coverage-matrix.md`, `docs/test-evidence/api-backed-channel-smoke.md` | The official staff frontend no longer exposes the retired API-backed staff route set, so staff-terminal route-to-API evidence is intentionally blocked by the integrated-terminal boundary. | Add a new operator route or call-center workflow and exercise it against Spring APIs before claiming staff-terminal live route/API coverage. |
| Customer channel | 8/10 | strong | Customer signup/login, accounts/detail/history, internal transfer, complaint entry, loan/card/payment/notification panels, and env-gated live API browser paths are documented in `docs/test-evidence/customer-onboarding-self-service.md`, `docs/test-evidence/api-backed-channel-smoke.md`, and `docs/test-evidence/live-route-api-execution.md` | Some live browser smokes are environment-gated; skipped Playwright tests are not pass evidence. | Rerun customer-web API/Keycloak smokes against a fresh synthetic stack for release evidence. |
| Electronic complaint portal | 8/10 | strong | Complaint intake, materials, reopen, answer drafting, approval, customer confirmation, live Keycloak evidence, and workflow failure-state smokes are documented in `docs/test-evidence/phase-5-complaint-workflow.md` and `docs/test-evidence/api-backed-channel-smoke.md` | Broader dispute/claim variants remain future work. | Add dispute-specific operator journeys only after the case workflow contract is extended. |
| Call-center 상담 전산 | 8/10 | partial | Spring/PostgreSQL now has an API-backed synthetic call-center workflow for masked customer search, interaction start/detail, redacted note entry, aftercall tasks, maker-checker protected `CALL_CENTER_ESCALATION`, complaint conversion with synthetic source reference after checker approval, close, history, role policy, and access audit. `apps/call-center-console` renders `CALL-101..CALL-106` through the shared channel UI, typechecks, has a Playwright manifest smoke, and includes a Keycloak token exchange route plus `call-agent01` maker and `call-manager01` checker live Compose browser smoke evidence with simulator tokens disabled. Evidence: `docs/test-evidence/call-center-console.md`, `docs/test-evidence/generated/call-center-console.json`, and `docs/test-evidence/evidence-gap-report.md` | The live evidence is local disposable Compose evidence, not hosted CI evidence or a production identity deployment; broader call-center exception/failure drills remain limited. | Rerun the local live API/Keycloak wrapper before portfolio recording and add failure-state variants only if they serve a demo/control gap. |
| FDS/AML 실질 적용 | 8/10 | strong | Held-transfer FDS release/block, AML closure, sanctions/PEP simulation, STR/model-card artifacts, and Spring/DuckDB analytics evidence exist: `docs/test-evidence/fds-aml-reconciliation.md`, `docs/test-evidence/hardening-h6-aml-fds-governance.md` | All lists, filings, and analytics are synthetic and deterministic; there is no real provider or regulator submission. | Keep external-provider adapters out of scope; add only simulator-backed variants. |
| FDS/AML admin/workflow/analytics | 8/10 | strong | FDS/AML console reads Spring case APIs and analytics evidence; parameter changes are approval-backed; DuckDB/Parquet data-platform evidence is reproducible: `docs/test-evidence/fds-analytics-spring-read-api.md`, `docs/test-evidence/hardening-h7-data-platform.md` | Live browser/Keycloak evidence for analytics read paths should be refreshed when a running stack is available. | Rerun FDS/AML console live smokes and data DQ gate before portfolio recording. |
| Deposits/fees/interest | 8/10 | strong | Deposit product rate versions, interest accrual/posting, fee policies, fee posting, and targeted fee-waiver reversal are Spring/PostgreSQL-backed: `docs/test-evidence/deposit-product-interest.md`, `docs/test-evidence/fee-policy-posting.md` | This is a synthetic product-ledger slice, not full retail deposit lifecycle coverage. | Add statement examples that connect interest/fee postings to customer-facing statements. |
| Card/loan | 7/10 | partial | Loan application/execution/repayment/prepayment and card issue/3DS/auth/capture/cancel/loss controls are API-backed with balanced ledger postings: `docs/test-evidence/loan-domain.md`, `docs/test-evidence/card-domain.md` | Card partial captures, merchant/category controls, replacement, dispute, and loan schedule-recast variants remain future work. | Add card dispute/replacement and partial prepayment variants after case workflow scope is stable. |
| Payment/notification/reporting bounded contexts | 7/10 | partial | Payment, notification, and reporting services have Spring APIs, Flyway schemas, Keycloak/JWKS paths, OpenAPI/AsyncAPI contracts, Redpanda/Testcontainers or Compose smoke evidence, and DTO schema gates: `docs/test-evidence/payment-service.md`, `docs/test-evidence/notification-service.md`, `docs/test-evidence/reporting-service.md`, `docs/test-evidence/contract-validation-hardening.md` | Payment-service coverage matrix remains `partial`; reporting browser smokes are conditional when reporting-service URL is configured. | Close remaining payment/reporting UI live-smoke gaps without weakening outbox or synthetic-only controls. |
| Security/JWKS/Keycloak | 8/10 | strong | Secure defaults, simulator-token opt-in, signed JWKS validation, route roles, step-up/trusted-device/session gates, live Keycloak realm evidence, and passkey evidence are documented in `docs/test-evidence/hardening-h2-secure-auth.md`, `docs/test-evidence/keycloak-live-realm-smoke.md`, and passkey evidence docs. | Broader session/device UX evidence and future authorization exception paths should be refreshed with a live stack. | Rerun security posture, live Keycloak, passkey readiness, and DAST evidence before release recording. |
| Audit/internal controls | 9/10 | strong | Append-only audit hash chain, reason-required access, masked PII, maker-checker approvals, WORM export simulation, break-glass review, SIEM drill, and governance artifacts are documented in `docs/test-evidence/hardening-h5-operational-security.md`, `docs/test-evidence/hardening-h8-governance-artifacts.md`, and `docs/test-evidence/evidence-gap-report.md` | This is lab-grade control evidence, not real SIEM/KMS/PAM integration. | Keep simulated controls explicit and add real-adapter exclusion tests if new integrations appear. |
| Operations/DR/evidence | 8/10 | strong | Synthetic load smoke, live disposable PostgreSQL backup/restore, multi-instance ledger drill, evidence refresh check, and governance evidence exist: `docs/test-evidence/load-test-summary.md`, `docs/test-evidence/postgres-backup-restore-drill.md`, `docs/test-evidence/hardening-h4-ha-dr-proof.md` | Does not prove cross-region DR, production load balancer failover, or enterprise backup operations. | Rerun live backup/restore and multi-instance drills before each evidence refresh. |
| K8s/Helm/Argo | 7/10 | partial | Kubernetes, Helm, Argo CD structural validation and scoped kind/Helm smoke for PostgreSQL plus core-banking are documented in `docs/test-evidence/platform-deployment-validation.md` | Ingress traffic, real TLS termination, Argo CD controller sync health, canary promotion, multi-node storage, and full worker rollout remain future work. | Add ingress/TLS/controller-sync evidence only in a disposable synthetic environment. |
| Contract/event validation | 8/10 | strong | OpenAPI lint/client/event gates, Kotlin controller source-to-OpenAPI path/method diffing, DTO schema parity for core ledger/inquiry/case subsets plus bounded contexts, and runtime event envelope fixture/source-marker validation are documented in `docs/test-evidence/contract-validation-hardening.md` and `docs/test-evidence/contract-runtime-evidence-boundary.md` | Full core-banking Spring/Jackson/springdoc DTO parity and exhaustive live broker producer/consumer coverage remain open. | Extend DTO schema parity to remaining core-banking API subsets and broaden live broker envelope tests. |
| Hosted CI | 3/10 | blocked | `.github/workflows/ci.yml` has the required jobs and `npm run ci:check-workflow` verifies the structure; blocked hosted runs are recorded in `docs/test-evidence/ci-hosted-run-status.md` | GitHub Actions jobs did not start because of billing/spending-limit runner allocation failure, so hosted CI is not green. | Resolve GitHub billing/spending-limit, rerun CI, and record run URLs without treating local fallback as hosted success. |
| Documentation consistency | 7/10 | partial | README, implementation coverage matrix, evidence gap report, live-route evidence, contract evidence, this scorecard, and demo script now use explicit status boundaries. | Current docs still include historical point-in-time evidence sections; the scorecard must be refreshed after each hardening PR. | Keep this scorecard, `docs/demo-scenarios/demo-video-script.md`, coverage matrix, README, and evidence gap report aligned after each feature merge. |

## Current Demo Posture

Use `docs/demo-scenarios/demo-video-script.md` as the portfolio walkthrough. The script intentionally separates live/API-backed paths from blocked, partial, or missing paths. The call-center scene now shows the API-backed workflow, dedicated shell, maker-checker escalation control, and live local Compose Keycloak/API evidence while still disclosing the local-only evidence boundary.

## Verification Commands To Refresh Before Recording

Minimum local refresh:

```bash
npm test
npm run validate:manifests
npm run packages:typecheck
npm run scripts:typecheck
npm run contracts:lint
npm run contracts:check-client
npm run contracts:check-events
npm run contracts:diff-openapi
npm run contracts:validate-runtime-events
npm run platform:validate
npm run security:posture-check
npm run formal:ledger
npm run evidence:pack
```

Environment-dependent refreshes:

```bash
npm run test:core-banking:integration
npm run test:payment-service:integration
npm run test:notification-service:integration
npm run test:reporting-service:integration
npm run load:synthetic
npm run postgres:backup-drill:docker-live
npm run dr:multi-instance-drill
```

Hosted CI remains a separate evidence class. If hosted GitHub Actions is blocked before runner startup, record it as `blocked` in `docs/test-evidence/ci-hosted-run-status.md`, not as green.
