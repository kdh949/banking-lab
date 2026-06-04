# Synthetic Incident Response Runbook

This runbook is for the banking-lab synthetic environment only. It must not be used as production operating procedure for real funds, real PII, real KYC, real payment networks, or external financial institution APIs.

## Scope

- Core banking API outage, ledger contention, disposable PostgreSQL restore, synthetic SIEM signal, and evidence refresh.
- H4 HA/DR drills and H8 governance evidence are the referenced artifacts.

## Drill Steps

1. Detect through synthetic health, audit, SIEM, or drill evidence.
2. Triage with ledger invariant, idempotency, and audit hash-chain checks.
3. Contain by freezing synthetic release/change activity and preserving generated evidence.
4. Recover through documented synthetic restart or disposable PostgreSQL restore drill.
5. Verify `sum(postings)=0`, projection equality, approval/workflow parity, and audit continuity.
6. Close with post-incident review, access-rights review, vulnerability tracker update, and deployment approval evidence.

## Evidence Commands

- `npm run dr:multi-instance-drill`
- `npm run postgres:backup-drill:docker-live`
- `npm run security:evidence`
- `npm run governance:evidence`
- `npm run evidence:refresh-check`

## Synthetic Boundary

All referenced data and controls are lab simulations. No real funds, PII, KYC, payment-network data, external bank API, real sanctions data, or regulator submission is used.
