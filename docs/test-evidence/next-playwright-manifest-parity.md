# Next Playwright Manifest Parity

Review date: 2026-06-03

## Scope

This evidence note covers the frontend parity scaffolding for the current Node retirement gate. The active Next manifest shell scope is:

- `customer-web`
- `staff-terminal`
- `complaint-portal`
- `ops-console`
- `audit-console`
- `fds-aml-console`
- `admin-console`

`admin-console` is now included as a target-stack surface on port 3007 with manifest-rendered platform control and privileged security-parameter screens. The gate remains blocked; the admin shell does not remove the non-synthetic passkey, evidence-refresh, or final retirement-review blockers.

## Added Playwright Coverage

The root `playwright.config.ts` starts all seven Next workspace dev servers through `webServer` entries and runs app-local specs under `apps/*/e2e`.

The default specs validate manifest-rendered metadata:

- masked PII/default masking text;
- reason-required control counts or labels;
- approval and maker-checker metadata;
- workflow timeline/status metadata;
- absence of app-router one-off business screen routes;
- absence of direct business API fetch calls in the shell page.

When `BANKING_LAB_E2E_API_BASE_URL` is set, the specs additionally prove a read-model API-backed smoke through the shared TypeScript API/auth clients:

- `customer-web` loads the synthetic owned account detail from Spring and displays a masked account number.
- `staff-terminal` loads the synthetic staff customer detail from Spring, displays masked phone data, and shows the generated audit event ID.
- `complaint-portal` loads a synthetic complaint case from Spring.
- `ops-console` loads a synthetic reconciliation item from Spring.
- `audit-console` loads audit event hash-chain evidence from Spring.
- `fds-aml-console` loads synthetic FDS and AML cases from Spring.
- `admin-console` loads a synthetic platform-control summary from Spring and keeps the Node reference boundary visible as blocked.

These tests do not claim full backend parity, workflow durability, non-synthetic WebAuthn, or Node retirement readiness. The admin console has a Keycloak browser smoke hook for `security-admin01`, but live admin browser evidence must be rerun with `BANKING_LAB_E2E_API_BASE_URL` and `BANKING_LAB_E2E_KEYCLOAK_BASE_URL` before treating it as live browser proof.

## Local Command

```bash
npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18081 npm run test:e2e
```

If Playwright browsers are not installed locally, run the standard Playwright browser install for this workspace before the command.
