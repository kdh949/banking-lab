# Final Command Refresh Evidence

Review date: 2026-06-11

Issue: https://github.com/kdh949/banking-lab/issues/88

Status: pass for local command refresh

Scope: PLAN final local command list. This is local synthetic-lab evidence only.
It is not hosted GitHub Actions green evidence and does not change the hosted CI
blocker tracked in #83.

Synthetic-only boundary: the refresh uses checked-in synthetic data,
simulators, local validators, and generated evidence artifacts only. It does not
use real customer money, real PII, real KYC/AML providers, card networks,
payment networks, regulator filing systems, or external financial-institution
APIs.

## Commands Run

| Command | Result | Evidence summary |
| --- | --- | --- |
| `npm test` | pass | 194 tests passed. |
| `npm run validate:manifests` | pass | 73 screen manifests validated. |
| `npm run packages:typecheck` | pass | `screen-engine`, `form-engine`, `api-client`, and `auth-client` typechecks passed. |
| `npm run scripts:typecheck` | pass | Script TypeScript project compiled with `--noEmit`. |
| `npm run contracts:lint` | pass | 4 OpenAPI files and 1 AsyncAPI file validated. |
| `npm run contracts:check-client` | pass | 186 `operationId` values matched 154 shared client methods or exemptions. |
| `npm run contracts:check-events` | pass | 17 AsyncAPI schema references validated. |
| `npm run contracts:diff-openapi` | pass | 150 core-banking, 14 payment-service, 16 notification-service, and 6 reporting-service controller operations matched checked-in OpenAPI. |
| `npm run contracts:validate-runtime-events` | pass | 17 synthetic envelope fixtures validated against 17 event schemas. |
| `npm run platform:validate` | pass | Kubernetes 27 resources, Helm 27 rendered resources, and Argo CD 2 applications validated; kubectl dry-run remained `skipped_no_cluster`. |
| `npm run security:posture-check` | pass | 22 security posture controls verified. |
| `npm run formal:ledger` | pass | 3335 states, 8241 transitions, and 15 invariants checked. |
| `npm run evidence:pack` | pass | Evidence-pack summary wrote 38 of 38 checks passing. |

Generated summary:
`docs/test-evidence/generated/final-command-refresh-2026-06-11.json`.

## Boundary

- This refresh closes the `final-command-refresh` partial row in
  `npm run portfolio:completion-audit`.
- It does not close hosted CI because GitHub Actions still fails before runner
  startup with `runner_id: 0` and `steps: 0`.
- It does not close live route API execution breadth because customer-web and
  staff-terminal route-to-API evidence remains environment-gated.
