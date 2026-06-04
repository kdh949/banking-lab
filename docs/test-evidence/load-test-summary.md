# Synthetic Load Test Summary

## Verdict

Status: pass

This is a synthetic smoke load, not a capacity benchmark. The default runner invokes the in-process lab request handler and uses synthetic seed data only. No real money, real PII, real KYC, payment networks, or external financial institution APIs are used.

## Command

```bash
npm run load:synthetic
```

## Scenario Mix

| Scenario | Runs | Requests | Failures | Latency budget breaches |
| --- | ---: | ---: | ---: | ---: |
| customer-account-lookup | 9 | 9 | 0 | 0 |
| customer-transfer-request | 9 | 9 | 0 | 0 |
| idempotency-retry-burst | 9 | 27 | 0 | 0 |
| staff-customer-lookup | 9 | 9 | 0 | 0 |
| fds-held-transfer-lookup | 9 | 9 | 0 | 0 |
| approval-inbox-lookup | 9 | 9 | 0 | 0 |

## Domain Checks

- Ledger invariant valid: true
- Audit hash chain valid: true
- Ledger transactions added: 18
- Idempotency replay responses: 18
- FDS held case visible: true
- Approval inbox visible: true
- Audit events appended: 36

## Evidence Boundary

- This evidence proves the load script, scenario coverage, idempotency retry handling, FDS held-case visibility, approval inbox visibility, ledger invariant preservation, and audit hash-chain continuity for a local synthetic smoke run.
- It does not prove production capacity, live Kubernetes deployment behavior, external Keycloak latency, real PostgreSQL backup/restore, or disaster recovery.
