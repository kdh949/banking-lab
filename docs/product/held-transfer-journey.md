# Held-transfer Journey

## Scenario

```text
Customer creates a high-value transfer to a first-time beneficiary
→ FDS holds it and creates no ledger transaction
→ customer receives journeyId and a security-review status
→ call-center agent supplies a business reason and opens masked customer context
→ interaction and redacted note are correlated to the journey
→ agent requests FDS handoff
→ FDS maker investigates in FDS201 and requests RELEASE or BLOCK
→ a different checker approves or rejects in APR101
→ RELEASE posts one balanced transfer exactly once
→ BLOCK posts nothing
→ customer status and synthetic notification are updated
→ customer, call center, and staff terminal can query the same journeyId
```

## State model

The journey is a projection over domain-owned state. It does not replace the ledger, transfer result, FDS case, approval, call-center, outbox, or notification tables.

| Journey status | Transfer/FDS meaning | Ledger rule | Customer copy |
| --- | --- | --- | --- |
| `HELD` | Transfer and FDS case await investigation | zero ledger transactions | Security review in progress |
| `INVESTIGATING` | An FDS reviewer owns the case | zero ledger transactions | Security review in progress |
| `RELEASE_REQUESTED` | Maker requested release; checker decision pending | zero ledger transactions | Security review in progress |
| `BLOCK_REQUESTED` | Maker requested block; checker decision pending | zero ledger transactions | Security review in progress |
| `POSTED` | Release approval executed | exactly one balanced transaction | Transfer completed |
| `BLOCKED` | Block approval executed | zero ledger transactions | Transfer not completed after security review |

Rejection returns the case to a safe review state defined by the FDS workflow; it never posts a transaction. A decision retry reuses a stable idempotency key and must return the first ledger result.

## Reference mapping

One journey may have at most one reference for singleton domain roles and many append-only event/audit/notification references.

| Reference type | Example prefix | Owner |
| --- | --- | --- |
| `CUSTOMER_TRANSFER_RESULT` | `TRR-` | Core Banking customer transfer |
| `FDS_CASE` | `FDS-` | Core Banking FDS |
| `CALL_CENTER_INTERACTION` | `CALL-` | Core Banking call center |
| `CALL_CENTER_ESCALATION` | `CALL-ESC-` | Core Banking call center |
| `OPERATOR_APPROVAL` | `APR-` | Core Banking approval |
| `LEDGER_TRANSACTION` | `TX-` | Core Banking ledger |
| `OUTBOX_EVENT` | `EVT-` | Domain outbox |
| `NOTIFICATION_DELIVERY` | `NDL-` | Notification Service |

References are additive. Replacing or deleting an existing mapping is not an allowed correction path. A correction adds a new journey event and, where required, a new reference.

## Append-only journey events

Each event records `journeyId`, event type, safe status, actor type/id/role where permitted, source type/reference, `requestId`, `traceId` when available, safe metadata, timestamp, and `syntheticOnly=true`. Free-form notes and raw PII are forbidden in journey metadata.

Expected event sequence:

1. `TRANSFER_HELD`
2. `CALL_CENTER_INTERACTION_STARTED`
3. `CALL_CENTER_NOTE_REDACTED`
4. `FDS_HANDOFF_REQUESTED`
5. `FDS_INVESTIGATION_STARTED`
6. `FDS_RELEASE_REQUESTED` or `FDS_BLOCK_REQUESTED`
7. `APPROVAL_APPROVED` or `APPROVAL_REJECTED`
8. `TRANSFER_POSTED` or `TRANSFER_BLOCKED`
9. `NOTIFICATION_REQUESTED`

Not every event is customer-visible. The customer projection filters internal event types and metadata.

## Authorization and audit

- Customer query uses the authenticated token `customerId`; a caller-controlled customer ID cannot broaden scope.
- Missing or other-customer journeys are concealed as the repository's established customer ownership policy requires.
- Staff query requires a non-blank business reason and appends a sensitive-read audit event with `journeyId`, actor, reason, and request/trace correlation.
- Customer output excludes risk score, FDS alerts, owner, approval actors, approval snapshots, note bodies, and internal reason text.

## Ledger invariants

- `HELD`, `INVESTIGATING`, `RELEASE_REQUESTED`, `BLOCK_REQUESTED`, and `BLOCKED` have no ledger transaction reference.
- `POSTED` has one ledger transaction reference and that transaction is balanced per currency.
- The journey projection never mutates balances or finalized transactions.
- The release idempotency key is stable for the FDS case.
- Notification failure cannot roll back a completed ledger transaction; notification retries are independent and idempotent.
