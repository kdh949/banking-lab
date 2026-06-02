# Next Playwright Manifest Parity

Review date: 2026-06-02

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

The specs validate manifest-rendered metadata only:

- masked PII/default masking text;
- reason-required control counts or labels;
- approval and maker-checker metadata;
- workflow timeline/status metadata;
- absence of app-router one-off business screen routes;
- absence of direct business API fetch calls in the shell page.

These tests do not claim backend parity, workflow durability, Keycloak enforcement, or Node retirement readiness.

## Local Command

```bash
npm run test:e2e
```

If Playwright browsers are not installed locally, run the standard Playwright browser install for this workspace before the command.
