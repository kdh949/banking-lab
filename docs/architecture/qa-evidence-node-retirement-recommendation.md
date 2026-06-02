# QA Evidence Node Retirement Recommendation

Review date: 2026-06-02

## Recommendation

Keep the Node reference runtime. Do not mark `docs/migration/node-retirement-gate.json` ready.

The correct current status is blocked because the target stack has not yet proven all Node reference parity, Keycloak enforcement, real Kafka delivery, security scans, observability smoke, and final retirement review required by `PLAN.md` and `AGENTS.md`.

## Basis

| Gate | Current status in gate file | QA recommendation |
| --- | --- | --- |
| `kotlin-spring-health` | Pass | Keep passed; live Spring `/health`, Flyway v007, and Docker/JDK target tests have been captured. |
| `node-reference-parity` | Pending | Keep pending until all 42 scenarios pass against target Kotlin/Spring or Next/Playwright tests. |
| `structured-error-contract` | Pass | Keep passed for response shape; probe-backed families still need real API replacement before final retirement. |
| `next-manifest-renderer` | Pass | Keep passed for six-shell manifest/Playwright parity; API-backed channel parity remains separate. |
| `evidence-refresh` | In progress | Keep in progress until target command outputs, security scans, observability smoke, and failure drills are captured. |
| `retirement-review` | Pending | Keep pending until a final review confirms no critical behavior depends on Node-only code. |

## Retirement Blockers

- The Node runtime still provides the executable oracle for audit, masking, maker-checker, complaint, FDS/AML, reconciliation, runtime APIs, and channel behavior.
- Kotlin/Spring health and ledger evidence are stronger, but full non-ledger route parity is not complete.
- Outbox persistence and status transitions have evidence, but real Kafka/Redpanda delivery is not proven.
- Durable PostgreSQL workflow state-machine evidence exists, but Temporal adapters or case-specific durable APIs are not complete.
- Keycloak/OAuth2/OIDC authorization enforcement is not proven.
- Next.js shell parity is covered by Playwright for six manifest shells, but API-backed channel workflows are pending.
- Required security verification evidence is incomplete.

## Required Before Ready

1. All 42 mapped parity scenarios pass in target test suites.
2. Spring Boot runs against PostgreSQL/Flyway and returns live `/health`.
3. API contract tests cover every structured error family.
4. Kafka/Redpanda outbox publish and idempotent consumer replay are tested.
5. Temporal or justified durable workflow tests cover complaint, FDS, AML, reconciliation, and holds.
6. Next.js/Playwright covers customer, staff, complaint, ops, audit, and FDS/AML surfaces.
7. Keycloak role/customer ownership and manager-only approval policies are enforced.
8. Semgrep, Trivy, SBOM, SCA, DAST, and observability smoke outputs are captured.
9. A final retirement review confirms no critical behavior depends on Node-only code.

## Node Reference Boundary

Node remains allowed only as:

- behavior reference
- parity oracle
- legacy demo comparison
- migration scenario source
- domain rule clarification

It must not be expanded as the final backend, final persistence layer, final security layer, final workflow engine, or final eventing system.
