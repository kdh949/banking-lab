# Phase 1 Foundation Test Evidence

## Acceptance Checks

- Local runtime serves customer web, staff terminal, and complaint portal shells.
- Mock auth supports role-shaped sessions for customer, staff, manager, complaint, auditor, and FDS/AML reviewer.
- Staff customer search requires a business reason and creates an audit event.
- Audit events verify as an append-only hash chain.
- High-risk account hold request is represented as a maker-checker approval.
- Screen manifests validate required audit, masking, role, template, and approval metadata.
- Seed ledger transactions are balanced double-entry postings.
- Balances are projected from postings.

## Commands

```bash
npm run validate:manifests
npm test
npm run generate:synthetic-data
npm run evidence:phase1
docker compose config
```

Generated machine-readable evidence is written to `docs/test-evidence/generated/phase-1-foundation.json`.
