# Next Playwright Manifest Parity

Review date: 2026-06-03

## Scope

This evidence note covers the frontend parity scaffolding for the current Node retirement MVP gate. The active Next manifest shell scope is:

- `customer-web`
- `staff-terminal`
- `complaint-portal`
- `ops-console`
- `audit-console`
- `fds-aml-console`

`admin-console` is excluded from this MVP gate because `docs/migration/node-retirement-gate.json` currently enumerates the six shells above for `next-manifest-renderer` evidence. Adding `admin-console` would widen the gate rather than proving the current retirement scope. The gate remains blocked.

## Added Playwright Coverage

The root `playwright.config.ts` starts all six Next workspace dev servers through `webServer` entries and runs app-local specs under `apps/*/e2e`.

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

These tests do not claim full backend parity, workflow durability, interactive Keycloak browser login, WebAuthn, or Node retirement readiness.

## Local Command

```bash
npm run test:e2e
env BANKING_LAB_E2E_API_BASE_URL=http://127.0.0.1:18081 npm run test:e2e
```

If Playwright browsers are not installed locally, run the standard Playwright browser install for this workspace before the command.
