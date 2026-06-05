# Admin Platform Evidence Coverage

Review date: 2026-06-05

## Scope

This evidence covers the `ADM-501` Admin Console slice for platform evidence and feature coverage visibility. The endpoint is a synthetic lab read surface only. It does not mutate infrastructure, read secrets, call real IAM, access real customer data, or connect to real financial institution systems.

## Implemented Target Evidence

- `GET /api/admin/platform/evidence-coverage?reason=...` returns curated evidence links and admin feature coverage references from the Spring Boot core-banking API.
- The endpoint requires a non-empty business reason and appends `ADMIN_EVIDENCE_COVERAGE_VIEW` audit events with `screenId=ADM-501`.
- The response contains bounded metadata only: evidence IDs, titles, repository paths, statuses, feature IDs, screen IDs, and API contract names.
- `@banking-lab/api-client` exposes `adminEvidenceCoverage(reason)`.
- `apps/admin-console` displays evidence coverage status in the API-backed admin panel when a Spring API URL is configured.
- `screen-manifests/admin-console/ADM-501.platform-evidence-coverage.json` declares the reason-required dashboard surface.

## Commands To Run For This Slice

```bash
npm run packages:typecheck
npm run next:admin-console:typecheck
npm run validate:manifests
npm test
npm run test:core-banking:integration -- --tests lab.banking.core.admin.AdminPlatformApiParityIntegrationTest --rerun-tasks
npm run test:e2e -- apps/admin-console/e2e/admin-console-parity.spec.ts
npm run evidence:refresh-check
npm run node:retirement-gate
```

## Invariants Covered

- Evidence reads are role-gated by the admin route policy.
- Evidence reads require a business reason and produce an audit hash-chain event.
- Evidence metadata is synthetic-only and does not expose evidence document contents.
- The Node reference boundary remains visible and blocked while the reference runtime is retained.
- No ledger postings, balances, approvals, or parameter values are mutated by the read path.

## Remaining Risk

This slice exposes curated coverage metadata, not a dynamic filesystem evidence browser. Future hardening can add signed evidence package hashes and last-verified command timestamps once the evidence pack generator owns a stable machine-readable index.
