# Frontend Channels Manifest Shells

## Scope

Agent D added Next.js App Router shells for these channel apps:

- `staff-terminal`
- `complaint-portal`
- `ops-console`
- `audit-console`
- `fds-aml-console`

The existing `customer-web` Next scaffold remains unchanged because it already renders from `screen-manifests/customer-web`.

## Runtime Shape

```text
Next App Router page
  -> app-local manifest loader
  -> screen-manifests/<app>/*.json
  -> manifest-rendered channel shell
```

Each channel shell renders declared screen metadata only:

- template type
- transaction code or screen id
- required roles
- audit and reason-required policy
- masking policy
- workflow states
- approval/maker-checker metadata
- declared endpoints, actions, widgets, fields, or sections

The shells do not call business APIs yet. This keeps the target Next.js channels aligned with the migration playbook while the Spring Boot API and generated client are still stabilizing.

## Control Coverage

The staff terminal exposes the transaction-code workspace, customer context, reason-required lookup count, masked-PII posture, approval inbox presence, and per-screen maker-checker declarations.

The complaint portal exposes customer self-service complaint intake and status manifests with SLA and timeline states.

The ops console exposes daily closing and reconciliation screens, including the requirement that mismatch adjustments use approval-controlled balanced adjustment transactions.

The audit console exposes the hash-chain review inquiry manifest and its append-only evidence columns.

The FDS/AML console exposes held-transfer review, release/block approval, AML STR simulation closure, workflow states, SLA, and masked customer context declarations.

## Coordinator Integration

Coordinator follow-up added root npm scripts and package-lock workspace entries for the five new channel apps.

Each new app still uses app-local `package.json` scripts and JavaScript/JSX source under `src/**`. App-local type checking runs with `tsc --allowJs --checkJs`.

Verified commands:

```bash
npm run next:staff-terminal:typecheck
npm run next:staff-terminal:build
npm run next:complaint-portal:typecheck
npm run next:complaint-portal:build
npm run next:ops-console:typecheck
npm run next:ops-console:build
npm run next:audit-console:typecheck
npm run next:audit-console:build
npm run next:fds-aml-console:typecheck
npm run next:fds-aml-console:build
```

## Next Work

Add Playwright E2E flows for each channel once routed target APIs and app-level served test configuration are available.
