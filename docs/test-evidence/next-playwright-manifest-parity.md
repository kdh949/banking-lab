# Next Playwright Manifest Parity

Review date: 2026-06-03

## Scope

This evidence note covers the frontend parity scaffolding for the current Node retirement gate. The active Next manifest shell scope is:

- `customer-web`
- `staff-terminal` (iWorks integrated terminal, not manifest-backed)
- `complaint-portal`
- `ops-console`
- `audit-console`
- `fds-aml-console`
- `admin-console`

`admin-console` is now included as a target-stack surface on port 3007 with manifest-rendered platform control and privileged security-parameter screens. The gate remains blocked; the admin shell does not remove the non-synthetic passkey or final retirement-review blockers.

## Added Playwright Coverage

The root `playwright.config.ts` starts all seven Next workspace dev servers through `webServer` entries and runs app-local specs under `apps/*/e2e`.

The default specs validate manifest-rendered metadata for manifest-backed channels and the iWorks shell contract for `staff-terminal`:

- masked PII/default masking text;
- reason-required control counts or labels;
- approval and maker-checker metadata;
- workflow timeline/status metadata;
- absence of app-router one-off business screen routes;
- absence of direct business API fetch calls in the shell page.

The structural Next scaffold tests also assert that target app directories and the synthetic runtime helper area no longer contain tracked legacy `public/index.html` static shells.

When `BANKING_LAB_E2E_API_BASE_URL` is set, the specs additionally prove a read-model API-backed smoke through the shared TypeScript API/auth clients:

- `customer-web` loads the synthetic owned account detail from Spring and displays a masked account number.
- `staff-terminal` renders the iWorks integrated terminal, exposes current client IP/server time through `/api/terminal-status`, and keeps old staff-terminal routes/manifests out of the app boundary.
- `complaint-portal` loads a synthetic complaint case from Spring.
- `ops-console` loads a synthetic reconciliation item from Spring.
- `audit-console` loads audit event hash-chain evidence from Spring.
- `fds-aml-console` loads synthetic FDS and AML cases from Spring.
- `admin-console` loads a synthetic platform-control summary from Spring and keeps synthetic-only runtime boundary controls visible.

These tests do not by themselves claim full backend parity, workflow durability, non-synthetic WebAuthn, or Node retirement readiness. The admin console now has live browser proof for `security-admin01` Keycloak token propagation into the Spring admin platform summary route, and the broader retirement gate is now ready through separate parity, passkey, evidence-refresh, and final-review artifacts.

## Local Command

```bash
npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18081 npm run test:e2e
scripts/run-core-banking-tests.sh :services:core-banking:bootJar
env COMPOSE_PROJECT_NAME=banking-lab-admin-smoke BANKING_LAB_POSTGRES_PORT=15449 BANKING_LAB_CORE_BANKING_PORT=18090 BANKING_LAB_KEYCLOAK_PORT=18091 BANKING_LAB_SECURITY_ENABLED=true BANKING_LAB_SECURITY_SIMULATOR_TOKENS_ENABLED=true BANKING_LAB_SECURITY_JWKS_URI=http://keycloak:8080/realms/banking-lab/protocol/openid-connect/certs BANKING_LAB_SECURITY_ISSUER=http://localhost:18091/realms/banking-lab BANKING_LAB_SECURITY_AUDIENCE=core-banking-api BANKING_LAB_CORS_ALLOWED_ORIGINS=http://localhost:3007,http://127.0.0.1:3007 docker compose --profile platform up -d --build postgres keycloak core-banking
curl -fsS http://127.0.0.1:18090/actuator/health
curl -fsS http://localhost:18091/realms/banking-lab/.well-known/openid-configuration
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18090 BANKING_LAB_E2E_KEYCLOAK_BASE_URL=http://localhost:18091 npx playwright test apps/admin-console/e2e/admin-console-parity.spec.ts --project=chromium
```

If Playwright browsers are not installed locally, run the standard Playwright browser install for this workspace before the command.
