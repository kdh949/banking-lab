# Cross-channel Held-transfer Workbench Evidence

## Scope and latest result

On 2026-08-10, `npm run demo:test:channels` passed against disposable local target-stack services. The run used synthetic data only and produced [machine-readable evidence](generated/cross-channel-held-transfer-demo.json) plus six browser screenshots. This is local execution evidence, not a hosted-CI-green claim.

## Executed path

```text
customer high-value first-beneficiary request
→ HELD with zero transfer ledger transactions
→ customer-safe journey inquiry
→ reason-gated masked call-center context
→ simulated identity state, redacted note, FDS handoff, disposition, close
→ FDS201 risk01 maker release request
→ APR101 manager01 independent checker with fresh simulated step-up
→ one balanced POSTED ledger transaction
→ durable outbox event and one masked customer notification
→ independent BLOCKED branch with zero ledger transactions
```

## Control evidence

The Playwright flow proves separated browser actors, the same journey in customer/call-center/staff views, customer ownership denials, HELD/POSTED UI, the call-center workflow, FDS201/APR101 operation, duplicate release rejection, notification visibility, and a browser-error-free run.

The browser/API flow proves the HELD response has no transaction identity and that the account transaction count is unchanged before approval. The final PostgreSQL verification in `scripts/verify-cross-channel-held-transfer-demo.sql` independently asserts:

- release produced exactly one transaction under the stable FDS release idempotency key with exactly two balanced KRW postings;
- the stable FDS release idempotency key did not create a second posting;
- block produced no transfer transaction;
- `risk01` maker differs from `manager01` checker;
- the interaction closed after reason-gated masked lookup, simulated identity pass, note redaction, FDS handoff, and disposition;
- POSTED and BLOCKED each produced one durable status outbox event and notification reference;
- journey/audit records retain actor, reason, journey, request, and trace correlation.

Notification delivery status may remain `PENDING` because no real SMS provider is used. The masked message and durable delivery record carry the final transfer status; provider delivery is intentionally synthetic and out of scope.

## Reproduction

Prerequisites are JDK 21, Node.js 24, Docker, and Playwright Chromium.

```bash
npm run demo:test:channels
```

The default wrapper cleans the disposable Compose project on exit. Set `BANKING_LAB_CHANNEL_DEMO_KEEP_COMPOSE=true` only for deliberate local investigation, then run `npm run demo:channels:reset` when finished.
