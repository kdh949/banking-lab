# Outbox Dead-letter Runbook

Synthetic lab only. Do not connect to real payment, notification, or bank networks.

## Symptoms

- `OutboxDeadLetterPresent`, `PaymentInstructionFailures`, `NotificationDeadLetterPresent`, or `ReportArtifactGenerationFailures` fires.
- Retry queue shows `FAILED` or `DEAD_LETTER` rows.
- Bounded-context workers stop publishing after broker errors.

## Detection

```sql
SELECT outbox_event_id, aggregate_type, event_type, status, retry_count, error_message
FROM outbox_events
WHERE status IN ('FAILED', 'DEAD_LETTER', 'PENDING')
ORDER BY created_at;
```

## Immediate Containment

- Do not manually mark events as published.
- Pause synthetic load generators.
- Keep duplicate broker delivery idempotent through inbox tables.

## Diagnosis Queries

```sql
SELECT status, count(*)
FROM outbox_events
GROUP BY status;
```

```sql
SELECT event_type, retry_count, next_retry_at, error_message
FROM outbox_events
WHERE status = 'DEAD_LETTER'
ORDER BY updated_at DESC;
```

## Recovery Steps

1. Confirm Redpanda/Kafka readiness.
2. Inspect worker logs for bounded error text.
3. Fix configuration or event contract mismatch.
4. Re-run the worker or targeted compose smoke.
5. Verify pending count returns to zero and dead-letter count is unchanged or intentionally explained.

## Evidence To Capture

- Outbox event ids.
- Worker command and result.
- Prometheus `outbox_pending_count` and `outbox_dead_letter_count`.
- Any payment, notification, or reporting worker failure counts.

## Rollback

Rollback code/config that introduced invalid event envelopes. Do not delete durable outbox rows without a documented synthetic lab reset.

## Escalation

Escalate to the owning bounded context when dead letters are service-specific.

## Post-incident Review

Add a contract test or AsyncAPI schema check if malformed events reached the outbox.
