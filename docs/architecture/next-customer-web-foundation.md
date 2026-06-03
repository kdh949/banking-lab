# Next Customer Web Foundation Architecture

## Runtime Intent

```text
Browser
  -> Next.js customer-web app
       -> App Router page
       -> manifest loader
       -> screen-manifests/customer-web/*.json
       -> future generated API client
       -> Spring Boot core-banking service
```

The existing static customer-web shell now lives at `legacy-node-reference/apps/customer-web/public/index.html` as a Node reference asset. The target `apps/customer-web` directory is reserved for the TypeScript/Next.js channel.

## Current Scaffold

- `apps/customer-web/package.json` defines the Next workspace.
- `apps/customer-web/src/app/page.tsx` renders the customer web workbench from manifests.
- `apps/customer-web/src/lib/manifestLoader.ts` reads `screen-manifests/customer-web` instead of hard-coding business screens.
- `npm run next:customer-web:typecheck` verifies TypeScript.
- `npm run next:customer-web:build` verifies the App Router production build.

## Dependency Control

Next 16.2.7 currently pins a vulnerable `postcss` transitive version. The root `package.json` uses an npm override and the lockfile resolves `postcss` to `8.5.10`.

Verification:

```bash
npm audit --omit=dev
node --test tests/nextScaffold.test.mjs
```

The scaffold should keep the override until a stable Next release removes the vulnerable transitive pin.

## Remaining Work

- Add the shared manifest renderer for the remaining app shells.
- Generate or stabilize the TypeScript API client after Spring OpenAPI parity.
- Customer account detail, transfer retry/failure, transaction history, held FDS status, durable held/failed transfer status parity, complaint entry, and complaint confirmation now have API-backed Playwright smoke.
- Customer account detail, transfer retry/failure, transaction history, held FDS status, durable held/failed transfer status parity, complaint entry, and complaint confirmation now have live Keycloak Authorization Code + PKCE login propagation through the Next BFF token exchange route, with Spring simulator tokens disabled and JWKS validation enabled.
- Remaining customer-web auth work is WebAuthn/MFA browser evidence; broader channel auth work now moves to ops, audit, complaint, and FDS/AML login propagation after the staff-terminal masked lookup/customer-change approval slice.
