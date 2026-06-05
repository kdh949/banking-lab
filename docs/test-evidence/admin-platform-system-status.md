# Admin Platform System Status

Review date: 2026-06-05

## Scope

This evidence covers the `ADM-601` Admin Console read surface for synthetic platform status, operational batch status, and monitoring links. It does not mutate infrastructure, trigger batches, rotate secrets, or call any real production monitoring system.

## Implemented Target Evidence

- `GET /api/admin/platform/system-status?reason=...` returns Spring-backed service status, latest operational batch metadata, and local monitoring link metadata.
- The endpoint reads existing PostgreSQL state for daily closings, EOD steps, interest posting batches, fee posting batches, and Outbox events.
- The endpoint requires a business reason and appends `ADMIN_SYSTEM_STATUS_VIEW` with `screenId=ADM-601`.
- `@banking-lab/api-client` exposes `adminSystemStatus(reason)`.
- `apps/admin-console` renders the system/batch/monitoring status in the API-backed admin panel.
- `screen-manifests/admin-console/ADM-601.system-batch-status.json` declares the reason-required dashboard surface.

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

- Reads are admin-role gated and reason-required.
- Reads produce audit hash-chain evidence.
- Batch status reads are projections from existing durable rows; no batch or ledger state is mutated.
- Service and monitoring statuses are synthetic lab metadata, not production availability claims.
- No real customer data, real PII, real funds, real infrastructure mutation, or real monitoring integration is used.

## Remaining Risk

This slice reports the latest durable rows and local profile links. Future hardening can add live health probes for Redpanda, Temporal, Keycloak, Prometheus, Grafana, Loki, and Tempo when a disposable platform profile is running.
