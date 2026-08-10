# Held-transfer Screen Map

## Product and lab boundary

| Channel | Product route/code | Goal role | Evidence label before runtime verification |
| --- | --- | --- | --- |
| Customer | `/login` | OIDC entry and opaque session establishment | `LIVE_API` — customer Compose and unified demo |
| Customer | `/dashboard` | account summary, held-transfer alert, journey inquiry | `LIVE_API` — customer Compose plus production build |
| Customer | `/accounts` | owned masked account list | `LIVE_API` — customer Compose |
| Customer | `/accounts/[accountId]` | owned detail and ledger-sourced history | `LIVE_API` — customer Compose and ownership denial |
| Customer | `/transfers/new` | idempotent held-transfer command | `LIVE_API` — customer Compose and unified demo API path |
| Customer | `/transfers/[referenceId]` | safe HELD/POSTED/BLOCKED status and journeyId | `LIVE_API` — unified demo HELD and POSTED captures |
| Customer | `/notifications` | customer-owned masked final-status delivery history | `LIVE_API` — unified demo durable notification capture |
| Customer | `/support` | inquiry guidance using journeyId | `LIVE_API` — built product route backed by customer status contracts |
| Call center | `/workspace` | softphone state, reason, masked 360, held transfer, note, FDS handoff, disposition | `LIVE_API` — unified demo plus Keycloak Compose lookup |
| Staff terminal | `CUS101` | reason-required masked customer context | `LIVE_API` — staff Compose |
| Staff terminal | `ACC101` | reason-required account context | `LIVE_API` — Spring-backed product implementation and build |
| Staff terminal | `TX101` | reason-required transfer/ledger references and journeyId | `LIVE_API` — Spring-backed product implementation and browser navigation |
| Staff terminal | `FDS201` | FDS investigation, release/block request | `LIVE_API` — unified demo maker capture |
| Staff terminal | `APR101` | independent approval/rejection with journey references | `LIVE_API` — unified demo checker capture |
| Staff terminal | `WRK003` | append-only cross-domain journey timeline | `LIVE_API` — Spring-backed product implementation and browser navigation |

The labels above describe current branch evidence. The unified command evidence is recorded in `docs/test-evidence/generated/cross-channel-held-transfer-demo.json`; route-specific Compose and browser commands are recorded in the progress log.

## Lab-only routes

| Route | Purpose | Boundary |
| --- | --- | --- |
| `/lab/evidence` | bounded API and integration evidence panels | simulator/token controls allowed only with explicit dev/test opt-in |
| `/lab/manifests` | manifest catalog and template metadata | structural evidence, not a product screen |
| `/lab/api-simulator` | deterministic fixture/API exerciser | always labeled `SIMULATED` |

Lab routes must not be linked as primary customer or agent navigation. Product pages must not render raw manifest tables, API base URLs, bearer tokens, token claims, or internal risk metadata.

## Reusable templates

- Customer transfer and FDS decision commands use the shared Command Template concepts: target, before/after state, reason where staff-owned, validation, approval, and audit.
- Customer, account, transfer, FDS, and journey reads use the Inquiry Template concepts: search, results, detail, masking, reason, and audit.
- Call-center interaction and FDS investigation use the Case Template concepts: status, owner, SLA/disposition, notes, timeline, and approval.
- Parameter screens remain outside this goal and continue to use the Parameter Template.

The staff terminal remains registry-driven and compact; it does not reintroduce staff manifests. Shared terminal primitives and domain-scoped screen files are the reuse boundary.

## Keyboard contract

- Product pages have one `h1`, associated form labels, visible focus, meaningful loading/error/empty states, and live-region status messages.
- Staff terminal supports transaction-code focus, Enter to execute navigation/search, keyboard tab traversal, and Escape to close dialogs.
- Softphone controls are native buttons with visible state text; color is never the only status signal.
