# Limit Service

## Goal

Manage daily, monthly, channel, user, account, transfer, card, and product limits
with posting-time enforcement, usage counters, maker-checker parameter changes,
and reversal release where applicable.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/parameters/**`
- `screen-manifests/staff-terminal/LIM-*.json`
- `docs/test-evidence/limit-enforcement.md`
- `formal/**`

## Target Folder Placement

Limit enforcement that affects posting belongs in `services/core-banking/.../ledger`
or `core/parameters`. Card-specific limit usage may live in `core/card` but must
coordinate with ledger posting outcomes.

## Backend Implementation Plan

- Persist limit policies and account/customer/channel-specific overrides.
- Enforce limits during transfer, withdrawal, card authorization/capture, payment,
  and other outbound commands.
- Track usage by day/month/channel and release usage on reversal where required.
- Route limit changes through maker-checker approval.

## Database / Migration Plan

Use account limits, limit usage counters, limit change requests, parameter
versions, and audit/outbox tables. Counters must be concurrency-safe.

## API / Event / Workflow Contracts

Expose staff limit inquiry/change APIs, posting-time limit checks, structured
`LIMIT_EXCEEDED` errors, and limit-change events.

## Frontend / Screen Manifest Plan

Use `LIM-101` for inquiry and `LIM-102` for change requests. Admin/parameter
screens may control default policy values.

## Security, Audit, Maker-Checker Controls

Limit increases are high-risk and require maker-checker. Reads require reason.
Posting-time rejections must produce structured errors without unsafe side
effects.

## Tests And Evidence

Test daily/monthly counters, channel-specific limits, concurrent attempts,
reversal release, unauthorized changes, self-approval rejection, and formal
limit invariants.

## Acceptance Criteria

- Limits are durable and enforced at posting time.
- Limit changes are approved and audited.
- Rejected commands leave no unsafe ledger state.
- Evidence covers concurrency and reversal behavior.

## Explicit Non-Goals

No real card network limit sharing and no external credit bureau limits.

