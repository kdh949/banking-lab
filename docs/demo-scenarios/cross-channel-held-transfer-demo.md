# Cross-channel Held-transfer Demo

This 90–120 second walkthrough is a local, synthetic-only supporting demo. The root portfolio story remains Payment Settlement & Ledger Reliability. No real customer, PII, money, institution API, notification provider, payment network, or settlement-finality claim is involved.

## Prepare

Run the deterministic proof before presenting:

```bash
npm run demo:test:channels
```

The command resets and starts disposable PostgreSQL, Redpanda, Core Banking, Notification Service, and three Next.js channels; creates separated customer, call-agent, FDS-maker, and checker actors; runs the release and block branches; verifies PostgreSQL invariants; records evidence; and cleans up. Expected terminal proof is one Playwright pass, `cross-channel held-transfer database invariants passed`, and a generated evidence path.

Use the six current screenshots under `docs/assets/channel-workbench/` while narrating.

## 90–120 second script

| Time | Screen | Narration and proof |
| --- | --- | --- |
| 0:00–0:12 | [Customer HELD result](../assets/channel-workbench/customer-held-transfer.png) | “A synthetic customer requests a KRW 5,000,000 transfer to a first-time beneficiary. The Spring API returns `HELD`, exposes a safe inquiry `journeyId`, and shows `not posted`; PostgreSQL has no transfer ledger transaction.” |
| 0:12–0:30 | [Call-center workspace](../assets/channel-workbench/call-center-fds-handoff.png) | “A separate simulated agent supplies a business reason, receives only masked customer 360 data, completes an explicitly simulated identity check, and starts a linked interaction. The note is redacted before persistence, the FDS handoff is correlated, and the interaction closes after an audited disposition.” |
| 0:30–0:45 | [FDS201 maker](../assets/channel-workbench/staff-fds201-release-request.png) | “The `risk01` FDS maker opens FDS201, claims the same journey case, and requests release. The transfer remains held and the ledger is still untouched.” |
| 0:45–1:02 | [APR101 checker](../assets/channel-workbench/staff-apr101-checker-release.png) | “A different `manager01` checker uses a fresh simulated WebAuthn step-up session in APR101. The screen shows maker-checker separation and executes one balanced double-entry posting.” |
| 1:02–1:18 | [Customer POSTED result](../assets/channel-workbench/customer-posted-transfer.png) | “The customer refreshes the same result. Status is now `POSTED`, exactly one `TX-` reference appears, and the customer-safe timeline correlates the channel handoff and review without exposing risk rules or staff notes.” |
| 1:18–1:30 | [Customer notification](../assets/channel-workbench/customer-transfer-notification.png) | “The committed decision also wrote a durable outbox event. Notification Service consumed it idempotently and exposes one masked, customer-owned final-status delivery.” |
| 1:30–1:42 | Terminal evidence | “The same command also runs a second `BLOCKED` branch with zero ledger transactions, denies another customer’s journey, transfer, and account reads, verifies maker differs from checker, and checks actor, reason, journey, request, and trace correlation directly in PostgreSQL.” |

## Reviewer follow-up

- Open `docs/test-evidence/generated/cross-channel-held-transfer-demo.json` for the actual local run identifiers and controls.
- Open `scripts/verify-cross-channel-held-transfer-demo.sql` for the database assertions behind the release, block, notification, call-center, and correlation claims.
- Open `docs/product/held-transfer-journey.md` for customer disclosure and state semantics.
- Reiterate that `POSTED` is internal balanced ledger posting, not external settlement finality.
