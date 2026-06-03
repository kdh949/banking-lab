# QA Evidence Node Retirement Recommendation

Review date: 2026-06-03

## Recommendation

Keep the Node reference runtime. Do not mark `docs/migration/node-retirement-gate.json` ready.

The correct current status is blocked even though all 42 mapped parity scenarios now pass in target-backed evidence and the API-backed channel parity gate is now closed. The remaining blockers are host crash shapes, API process crash after durable ledger/outbox commit, outbox tracing, non-synthetic passkey operations, evidence-refresh completion, and final retirement review required by `PLAN.md` and `AGENTS.md`.

## Basis

| Gate | Current status in gate file | QA recommendation |
| --- | --- | --- |
| `kotlin-spring-health` | Pass | Keep passed; live Spring `/health`, Flyway through v010, live Temporal worker smoke, and Docker/JDK target tests have been captured. |
| `node-reference-parity` | Pass | Keep passed; all 42 scenarios are mapped and target-backed, while the Node runtime remains preserved until final retirement. |
| `structured-error-contract` | Pass | Keep passed for response shape; nine required families now have real Spring route coverage, while `INTERNAL_RUNTIME_ERROR` remains profile-only by design. |
| `next-manifest-renderer` | Pass | Keep passed for target TypeScript screen-engine manifest parity plus six-shell manifest/Playwright parity; API-backed channel parity remains separate. |
| `api-backed-channel-parity` | Pass | Keep passed; six-channel read-model smoke, customer transfer retry/failure/history/held-status/held-failed-status/complaint-entry/complaint-confirmation browser smoke, customer-web Keycloak propagation for all current API-backed customer paths, staff-terminal Keycloak propagation for masked lookup, privileged unmask, branch-maker/manager-checker customer-change approval, and WebAuthn required-action completion, complaint-portal Keycloak propagation for answer approval plus workflow failure-state, ops-console Keycloak propagation for reconciliation adjustment plus workflow failure-state, audit-console Keycloak propagation for hash-chain read-model evidence, FDS/AML-console Keycloak propagation for risk read-model, FDS release/block approval, AML closure approval, and workflow failure-state, staff privileged unmask browser smoke with structured denial visibility, customer change approval browser command smoke with self-approval rejection, complaint answer approval browser command smoke, complaint/FDS/AML/reconciliation workflow failure-state browser smokes, FDS release/block approval browser command smokes, AML closure approval browser command smoke, reconciliation adjustment approval browser command smoke, and synthetic WebAuthn policy/recovery segregation evidence are proven. A fresh API-backed Playwright run on 2026-06-03 passed 35 tests with 7 Keycloak-dependent tests skipped when no Keycloak URL was configured. |
| `evidence-refresh` | In progress | Keep in progress until full target command outputs, host crash shapes, API process crash after durable ledger/outbox commit, and outbox tracing are captured. |
| `retirement-review` | Pending | Keep pending until a final review confirms no critical behavior depends on Node-only code. |

## Retirement Blockers

- The Node runtime still provides the executable oracle and rollback comparison until final retirement, even though the direct mapped route parity is target-backed.
- Kotlin/Spring and Next evidence now cover all 42 mapped scenarios, but operational independence from Node is not proven.
- Outbox persistence, Redpanda publish-after-durable-insert, duplicate replay idempotency, retry, DLQ behavior, and crash-before-mark-published replay through inbox idempotency are proven through `RedpandaOutboxDeliveryIntegrationTest`; scheduled worker operations, Micrometer/Prometheus actuator metrics, a live Compose worker-container restart-before-publish drill, and a live Compose post-broker-ack worker crash/replay drill are now proven. API process crash after durable ledger/outbox commit and outbox tracing are still pending.
- Durable PostgreSQL workflow state-machine evidence, Temporal test-environment contracts, live Temporal worker/server smoke, domain-table workflow ID persistence, Temporal worker Micrometer/Prometheus metrics, a synthetic Temporal retry drill, a live Temporal SDK worker restart drill, live Compose Temporal worker container restart drills for all current case types, live Compose Temporal server restart drills for all current case types, live Compose PostgreSQL restart drills for all current case types, local Prometheus/Grafana/Loki/Tempo readiness plus Prometheus scrape evidence, basic Spring HTTP OpenTelemetry trace/log correlation, Temporal signal/completion trace/log correlation for all current workflow case types, live Temporal rejection/self-approval failure transition trace/log correlation, Loki-ingested workflow failure logs, a firing `TemporalWorkflowFailed` ruler alert, and Grafana dashboard validation now exist; host crash-shape evidence, API process crash after durable ledger/outbox commit, and outbox tracing are not complete.
- Spring now proves signed JWT/JWKS authorization for route roles, ownership, invalid signature, expiry, disabled simulator fallback, manager-only approval, actor binding, denial audit events, CORS-readable auth denial for browser clients, live Keycloak realm import, public channel token issuance, customer-claim propagation, TOTP required-action blocking, WebAuthn required-action blocking, synthetic WebAuthn policy and passkey recovery role segregation, customer-web browser token propagation for all current API-backed customer paths, staff-terminal branch/checker browser token propagation plus privileged unmask and WebAuthn virtual-authenticator completion, complaint-portal handler/checker browser token propagation, ops-console operator/checker browser token propagation, audit-console auditor browser token propagation, and FDS/AML risk reviewer/checker browser token propagation.
- Next.js shell parity is covered by Playwright for six manifest shells, six-channel API-backed read-model smoke is covered against a live Spring API, and customer transfer retry/failure/history/held-status/held-failed-status/complaint-entry/complaint-confirmation, customer-web Keycloak propagation for all current API-backed customer paths, staff-terminal Keycloak propagation for masked lookup/privileged unmask/customer-change approval/WebAuthn, complaint-portal Keycloak propagation for answer approval/workflow failure-state, ops-console Keycloak propagation for reconciliation adjustment/workflow failure-state, audit-console Keycloak propagation for hash-chain read-model evidence, FDS/AML-console Keycloak propagation for risk command/failure-state paths, customer change, complaint answer, complaint/FDS/AML/reconciliation workflow failure-states, FDS release/block, AML closure, and reconciliation adjustment approvals now have browser smoke evidence. Non-synthetic passkey operations are not claimed.
- Required security verification evidence is now non-skipped for the current local synthetic slice: `npm run security:evidence` captures passing npm audit, Semgrep SAST, Trivy filesystem scan, CycloneDX SBOM, and ZAP baseline DAST evidence. Local observability readiness, Prometheus scrape, Loki ingestion, Loki alert, and Grafana dashboard evidence are captured in `docs/test-evidence/observability-stack-smoke.md`.

## Required Before Ready

1. All 42 mapped parity scenarios pass in target test suites. This is now satisfied, but it is not sufficient by itself for retirement.
2. Spring Boot runs against PostgreSQL/Flyway and returns live `/health`.
3. API contract tests cover every structured error family.
4. Kafka/Redpanda outbox publish, crash-before-mark-published replay, idempotent consumer replay, operational worker scheduling, worker metrics, live Compose worker-container restart-before-publish, and live Compose post-broker-ack worker crash/replay are tested; API process crash after durable ledger/outbox commit and outbox tracing are still required before production-like retirement.
5. Temporal test-environment and live worker/server evidence cover complaint, FDS, AML, reconciliation, and holds, with workflow IDs persisted in domain tables, plus SDK worker restart evidence, Compose worker container restart evidence for all current case types, Compose Temporal server restart evidence for all current case types, and Compose PostgreSQL restart evidence for all current case types; host crash-shape evidence and API process crash after durable ledger/outbox commit are still required.
6. Next.js/Playwright covers customer, staff, complaint, ops, audit, and FDS/AML surfaces.
7. Keycloak role/customer ownership and manager-only approval policies are enforced with signed-token/JWKS validation plus live realm import, MFA/WebAuthn required-action evidence, synthetic WebAuthn policy/recovery role segregation, current customer-web login propagation, staff-terminal branch/checker/privileged-unmask/WebAuthn propagation, complaint-portal handler/checker propagation, ops-console operator/checker propagation, audit-console auditor propagation, and FDS/AML risk reviewer/checker propagation.
8. Semgrep, Trivy, SBOM, SCA, DAST, and observability outputs are captured; current evidence covers SCA/SAST/Trivy/SBOM/DAST, local Prometheus/Grafana/Loki/Tempo readiness, Prometheus scrape smoke, basic Spring HTTP trace/log correlation, all-current-case Temporal signal/completion workflow trace/log correlation, live Temporal rejection/self-approval failure transition trace/log correlation, Loki ingestion, a firing Loki ruler alert, and Grafana dashboard validation.
9. A final retirement review confirms no critical behavior depends on Node-only code.

## Node Reference Boundary

Node remains allowed only as:

- behavior reference
- parity oracle
- legacy demo comparison
- migration scenario source
- domain rule clarification

It must not be expanded as the final backend, final persistence layer, final security layer, final workflow engine, or final eventing system.
