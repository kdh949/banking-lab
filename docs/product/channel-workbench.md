# Cross-channel Banking Workbench

## Portfolio position

The Payment Settlement & Ledger Reliability slice remains the first message in the root README and the primary portfolio proof. The Channel Workbench is a supporting vertical slice: it demonstrates how one customer-visible transfer exception is handled across channels without weakening ledger integrity, settlement semantics, or internal controls.

The workbench is synthetic-only. It does not model real customers, real funds, real KYC, real financial institutions, real payment networks, or real notification providers.

## Product promise

A reviewer can follow one high-value first-beneficiary transfer from customer request to FDS hold, call-center handling, FDS decision request, independent approval, final ledger or no-ledger outcome, and customer notification. Every channel displays the same business `journeyId` while preserving channel-specific disclosure boundaries.

## Evidence labels

Every screen, route, and evidence claim uses one of these labels.

| Label | Required evidence | Allowed claim |
| --- | --- | --- |
| `LIVE_API` | A product route calls a target Spring or bounded-context API, persists or reads the canonical PostgreSQL state, and has a passing integration or configured browser test. | The implemented behavior is API-backed for the stated synthetic scope. |
| `SIMULATED` | A deterministic fixture or local-only simulator is deliberately used and the UI labels the boundary. It must not be the only proof for a `LIVE_API` claim. | The interaction is a controlled lab simulation. |
| `REFERENCE` | Archived Node/oracle behavior, historical evidence, or a non-target comparison is shown without target implementation credit. | The artifact helps explain parity or history only. |
| `PLANNED` | The product or API path is absent, skipped, or not yet verified. | No implementation or pass claim is allowed. |

`route-backed-live-gated` in the broader coverage matrix means the route exists but depends on a configured running stack. It becomes `LIVE_API` for this workbench only when the relevant command has actually passed and the result is recorded in the progress log.

## Bounded product routes

The first `LIVE_API` scope is intentionally narrow:

- Customer: login, dashboard, accounts, account detail, new transfer, transfer status, support.
- Call center: one `/workspace` screen for reason, customer context, held transfer, redacted note, FDS handoff, disposition, and close.
- Staff terminal: `CUS101`, `ACC101`, `TX101`, `FDS201`, `APR101`, and `WRK003`.

Manifest catalogs, raw API exercisers, token simulators, contract probes, and generated evidence belong under explicit `/lab/*` routes. They remain valuable but are not product navigation.

## Shared vocabulary

- `journeyId`: stable customer/business inquiry number that correlates references across bounded domains.
- `traceId`: technical request/trace correlation value; it can change between requests and is not a customer inquiry number.
- `reference`: domain-owned identifier such as transfer result, FDS case, interaction, escalation, approval, ledger transaction, outbox event, or notification delivery.
- `HELD`: security review is in progress and no ledger transaction exists.
- `POSTED`: the approved internal transfer created exactly one balanced ledger transaction. It does not claim external settlement finality.
- `BLOCKED`: the approved FDS block completed without a ledger transaction.

## Disclosure boundaries

Customers may see the journey inquiry number, transfer status, amount/currency, masked accounts, safe status message, and notification state. Customers must not see risk scores, rule hits, staff identifiers, approval notes, redacted source text, or internal workflow metadata.

Staff may see only data allowed by role. Customer/account/transaction/journey reads require a business reason and create audit events. PII is masked by default. FDS makers cannot approve their own decision requests.

## Success criteria

The workbench is complete only when the 12 Definition-of-Done items in `docs/codex/channel-workbench-progress.md` are backed by actual commands. A fixture-only or skipped browser run cannot close a `LIVE_API` item.
