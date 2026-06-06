# PostgreSQL Restore Runbook

Synthetic lab only. Do not restore real customer databases or real bank data with this repository.

## Symptoms

- Spring services cannot connect to PostgreSQL.
- `AuditAppendFailure` fires.
- Health checks report audit hash-chain or database failures.

## Detection

```sql
SELECT count(*) FROM audit_hash_chain_lock;
SELECT count(*) FROM audit_events;
SELECT count(*) FROM ledger_transactions;
```

## Immediate Containment

- Stop synthetic writers.
- Preserve failed database volume or dump for evidence if available.
- Do not run destructive cleanup until evidence is captured.

## Diagnosis Queries

```sql
SELECT audit_event_id, payload_hash, previous_event_hash, created_at
FROM audit_events
ORDER BY created_at DESC
LIMIT 20;
```

```sql
SELECT status, count(*)
FROM outbox_events
GROUP BY status;
```

## Recovery Steps

1. Restore the latest synthetic backup to a disposable database.
2. Run Flyway migration validation.
3. Run health and ledger integrity checks.
4. Verify audit hash-chain continuity.
5. Restart services with private/local credentials, not committed secrets.

## Evidence To Capture

- Backup artifact path.
- Restore command.
- Health response.
- Audit hash-chain check result.
- Ledger integrity command result.

## Rollback

If restore validation fails, discard the restored database and retry from an earlier synthetic backup. Do not patch data in place.

## Escalation

Escalate to platform owner when restore fails due to migration, volume, or credential issues.

## Post-incident Review

Update backup frequency, restore drill coverage, and secret-handling documentation if gaps were found.
