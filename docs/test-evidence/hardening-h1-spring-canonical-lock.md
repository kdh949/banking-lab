# H1 Spring Canonical Lock Evidence

Date: 2026-06-05

Status: pass

## Scope

This H1 evidence locks the Spring Boot target path as canonical for the current synthetic lab scope and keeps the Node runtime under `legacy-node-reference/` as an oracle only.

No real funds, real PII, real KYC, real payment networks, or real external financial institution APIs were used.

## Commands

```bash
node --test tests/stackRetirementAreaAudit.test.mjs
npm run retirement:stack-audit
npm run node:retirement-gate
npm run parity
npm test
npm run validate:manifests
npm run test:screen-engine
npm run scripts:typecheck
npm run next:customer-web:build
npm run next:staff-terminal:build
npm run next:complaint-portal:build
npm run next:ops-console:build
npm run next:audit-console:build
npm run next:fds-aml-console:build
npm run next:admin-console:build
```

## Results

- `node --test tests/stackRetirementAreaAudit.test.mjs`: pass, 3 tests.
- `npm run retirement:stack-audit`: pass.
- `npm run node:retirement-gate`: pass, ready.
- `npm run parity`: pass after sandbox escalation for localhost listener permissions; 42 mapped Node reference scenarios remained target-backed, 142 Node/reference tests passed, 87 manifests validated, and 10 screen-engine tests passed.
- `npm test`: pass after sandbox escalation for localhost listener permissions, 142 tests.
- `npm run validate:manifests`: pass, 87 manifests.
- `npm run test:screen-engine`: pass, 10 tests.
- `npm run scripts:typecheck`: pass.
- All seven `npm run next:<app>:build` commands passed for customer-web, staff-terminal, complaint-portal, ops-console, audit-console, fds-aml-console, and admin-console.

## New Guard

`npm run node:retirement-gate` now invokes both the retirement boundary audit and the stack-area audit before a ready result can be accepted.

`tests/stackRetirementAreaAudit.test.mjs` now injects an in-memory canary source path:

```text
apps/customer-web/src/__legacy_node_reference_canary.ts
```

The canary imports `legacy-node-reference/runtime/server.mjs`. The gate must fail with a target-stack dependency error for that injected path. The canary is not written to the real target tree, so the full `npm test` suite is not polluted by a transient planted file.

## Controls

- Target backend remains Kotlin/Spring Boot and PostgreSQL/Flyway.
- Channel apps remain Next.js/React and use shared Spring-facing API contracts.
- Target source areas must not import or call `legacy-node-reference`, `runtime/server.mjs`, or `runtime/labApp.mjs`.
- Node remains available only as archived oracle/reference material for parity.

## Limitations

This H1 slice proves target-path Node dependency rejection and current parity retention. It does not implement H2-H8 security, DB trigger, HA/DR, operational-security, AML/reporting, data-platform, or governance controls.
