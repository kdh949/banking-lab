# Hardening H2 Secure Auth Evidence

Date: 2026-06-05

## Scope

H2 flips the target Spring runtime to secure defaults and adds production-shaped synthetic auth controls:

- `BANKING_LAB_SECURITY_ENABLED` defaults to `true`;
- simulator tokens default to disabled and require both `BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true` and `BANKING_LAB_DEV_SIMULATOR_TOKEN=true`;
- signed JWT/JWKS remains the production-shaped path;
- high-risk staff/ops commands require fresh MFA/WebAuthn step-up claims;
- customer transfer and staff session validation require an active synthetic trusted device;
- revoked synthetic sessions are denied before domain execution.

All controls are synthetic lab controls. No real customer data, real device intelligence, real financial network, real KYC, or external financial institution API is used.

## Changed Control Surface

- New Flyway migration: `db/migrations/V026__security_auth_hardening_controls.sql`.
- New tables: `trusted_devices`, `revoked_sessions`.
- New API: `GET /api/auth/session` for synthetic session/device binding validation.
- New script: `npm run security:posture-check`.
- New structured auth errors: `STEP_UP_REQUIRED`, `TRUSTED_DEVICE_REQUIRED`, `SESSION_REQUIRED`, `SESSION_EXPIRED`, `SESSION_REVOKED`.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run security:posture-check` | pass; 6 secure-default controls verified |
| `npm run scripts:typecheck` | pass |
| `npm run test:core-banking:integration -- --tests lab.banking.core.security.SecurityDefaultsIntegrationTest --tests lab.banking.core.security.JwksAuthorizationIntegrationTest --rerun-tasks` | pass after sandbox escalation and fixture seed fix |
| `npm run k8s:validate` | pass; 15 resources, kubectl client dry-run skipped because no reachable local cluster |
| `npm run helm:template` | pass; Helm rendered 9 resources |
| `npm test` | pass; 142 tests |
| `npm run validate:manifests` | pass; 87 manifests |
| `npm run test:screen-engine` | pass; 10 tests |
| `npm run packages:typecheck` | pass |
| `npm run test:core-banking:integration -- --rerun-tasks` | pass; full Spring/Testcontainers integration suite |
| `npm run formal:ledger` | pass; 3335 states, 8241 transitions, 15 invariants |
| `npm run node:retirement-gate` | pass; ready |
| `npm run security:evidence` | pass after sandbox escalation; npm audit, Semgrep, Trivy, and SBOM passed; DAST skipped because `BANKING_LAB_DAST_URL` was not set |
| `npm run test:core-banking:unit -- --rerun-tasks` | pass after sandbox escalation and unit health-slice security-filter exclusion |

## Failures And Fixes

- Sandboxed Gradle runs failed with `java.net.SocketException: Operation not permitted` while creating Gradle file-lock sockets. Re-ran the same Gradle commands with approved escalation.
- The first JWKS H2 test run failed with `WORKFLOW_STATE_VIOLATION` because the test fixture lacked an effective FDS parameter seed for the customer-transfer success path. Fixed by reseeding the existing synthetic FDS parameter fixture in `JwksAuthorizationIntegrationTest`.
- The first full integration run failed two observability actuator tests because the new integration-test profile shadowed actuator exposure. Fixed by adding `management.endpoints.web.exposure.include=health,info,metrics,prometheus` to the integration-test profile.
- The first unit run failed `HealthControllerTest` context loading because `@WebMvcTest` loaded the security filter without its full dependencies after the default changed to security-on. Fixed by setting `banking-lab.security.enabled=false` for that health-only unit slice.
- `npm run security:evidence` first failed under sandbox due npm registry and Docker socket restrictions. Re-ran with approved escalation. DAST was not run because no live `BANKING_LAB_DAST_URL` was supplied.

## Invariants And Controls

- Ledger invariants are not changed by H2. `npm run formal:ledger` still passes.
- API authorization is stricter by default: unauthenticated `/api/*` requests are rejected, simulator tokens are refused without explicit dev opt-in, and denial audit events are appended.
- High-risk routes now fail before domain mutation when step-up is missing.
- Unknown or revoked synthetic device/session contexts fail before customer transfer or staff session validation.
- Node remains oracle-only; `npm run node:retirement-gate` still passes.
