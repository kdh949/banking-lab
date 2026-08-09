# Channel Workbench Security Architecture

## Trust boundaries

```text
Browser product route
  -> Next.js BFF/session boundary
       -> OIDC Authorization Code + PKCE
       -> encrypted/httpOnly/SameSite session cookie
       -> server-side bearer token handling
  -> Spring Resource Server
       -> token ownership or staff role policy
       -> reason, masking, audit, maker-checker
  -> PostgreSQL / outbox
  -> Redpanda
  -> Notification Service inbox and synthetic provider sink
```

Browser product JavaScript must not receive, read, log, or persist bearer tokens. OIDC state and PKCE verifier are short-lived server-session data. An explicit `/lab/*` simulator may use a test token only when both dev/test opt-ins are enabled and the UI labels the mode `SIMULATED`.

## Customer boundary

- Customer APIs derive ownership from the validated `customerId` claim.
- Body/query customer identifiers are compatibility inputs only where existing contracts require them; they cannot broaden ownership.
- Journey and transfer lookups conceal cross-customer references.
- Customer responses contain safe statuses and masked account identifiers only.
- Risk score, rule hits, FDS owner, maker/checker identities, approval snapshots, staff notes, and internal reasons are forbidden.

## Staff boundary

- Sensitive customer, account, transaction, FDS, and journey reads require a non-blank business reason.
- Every permitted read appends an audit event with actor, role, screen, reason, target, journeyId, and request/trace correlation when available.
- PII is masked by default. Time-boxed unmask controls remain separate and are not needed by the held-transfer flow.
- Call-center note text is redacted before persistence; audit and journey metadata store only redaction facts and identifiers.

## Maker-checker boundary

- FDS release and block requests are high-risk operations.
- The maker identity and role come from the authenticated principal or are checked against it.
- The checker must be a different actor with an allowed role.
- Self-approval returns `MAKER_CHECKER_SELF_APPROVAL_REJECTED` and leaves case, transfer, ledger, and journey state unchanged.
- Release execution uses a stable FDS-derived idempotency key. Block execution never calls the ledger.

## Correlation values

| Value | Stability | Audience | Logging rule |
| --- | --- | --- | --- |
| `journeyId` | business-lifecycle stable | customer and staff | safe to log as a synthetic business reference |
| `requestId` | request stable | staff/operations | propagate from `x-request-id` or generate |
| `traceId` | distributed trace stable | engineering/operations | W3C `traceparent`; never treat as customer inquiry number |
| bearer token | secret | server only | never log or expose to product JS |

Logs and trace attributes must not include raw PII, note bodies, passwords, tokens, authorization headers, risk rule details, or approval internal notes.

## Failure behavior

- Ownership/role failures use the structured authorization error family.
- Missing staff reason uses `POLICY_REASON_REQUIRED`.
- Self-approval uses `MAKER_CHECKER_SELF_APPROVAL_REJECTED`.
- Invalid journey/workflow transitions use `WORKFLOW_STATE_VIOLATION`.
- Missing synthetic references use `RESOURCE_NOT_FOUND` without revealing other-customer existence.
- Notification failure is durable and retryable; it does not turn a posted ledger transaction into a failed transfer.

## Negative evidence

The completion gate must prove other-customer denial, missing-reason denial, masked staff output, note redaction, self-approval denial, no-ledger HELD/BLOCKED behavior, exactly-once release, and absence of bearer-token storage in product source.
