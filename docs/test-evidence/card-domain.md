# Card Domain Evidence

Review date: 2026-06-04

## Scope

Phase 3 adds a synthetic-only card vertical slice in the target Kotlin/Spring Boot stack:

- card issuance with tokenized PAN storage only;
- 3DS simulation for high-value authorizations;
- card authorization hold creation and cancellation;
- card capture posting through balanced ledger entries;
- capture reversal through append-only ledger reversal;
- card daily/monthly/single limits with `LIMIT_EXCEEDED`;
- lost-card status transition that blocks later authorizations.

No raw PAN, real card-network integration, real customer data, external issuer/acquirer API, KYC provider, or real funds path is used.

## Implemented Target Evidence

- `db/migrations/V023__card_domain.sql` creates `cards`, `card_limits`, `card_limit_usage_counters`, `card_3ds_simulations`, `card_authorizations`, and `card_captures`.
- `cards.pan_token` has a database check that rejects raw 12-19 digit PAN-shaped values.
- `LedgerCommandService` now posts `CARD_CAPTURE` through append-only balanced postings against the customer account and synthetic bank card clearing account.
- `CardService` exposes issuance, 3DS simulation, authorization hold, capture, authorization cancellation, capture reversal, lost-card reporting, and card detail APIs.
- `packages/api-client` and customer web include an API-backed card smoke path for issuance, 3DS, authorization, capture, and lost-card reporting.
- `screen-manifests/customer-web/CWB-601..606` model the customer card command screens.

## Commands Run

```bash
npm run test:core-banking:integration -- --tests lab.banking.core.card.CardDomainIntegrationTest
npm run packages:typecheck
npm run next:customer-web:typecheck
npm run validate:manifests
npm run test:screen-engine
```

## Result

- `CardDomainIntegrationTest`: passed after rerun outside the sandbox because Gradle file-lock socket creation was blocked by sandbox permissions.
- The first implementation run exposed an incorrect over-limit assertion path where a mismatched high-value 3DS check failed before limit enforcement. The test was corrected to use a sub-3DS amount that exceeds the remaining daily limit, and the rerun returned structured `LIMIT_EXCEEDED`.
- `npm run packages:typecheck`: passed.
- `npm run next:customer-web:typecheck`: passed.
- `npm run validate:manifests`: passed with 87 manifests.
- `npm run test:screen-engine`: passed.

## Invariants Verified

- Raw PAN-shaped tokens are rejected and no card row is inserted.
- Tokenized PAN rows remain synthetic and no raw PAN-shaped `pan_token` value is stored.
- High-value authorization without a matching 3DS simulation returns `WORKFLOW_STATE_VIOLATION` and creates no authorization or hold.
- Authorization hold decreases available balance and increases hold balance.
- Daily card-limit breach returns `LIMIT_EXCEEDED` without creating another authorization.
- Card capture posts one balanced `CARD_CAPTURE` ledger transaction and one durable `CardCapturePosted` outbox event.
- Capture releases the active hold without mutating ledger source rows.
- Capture reversal posts a balanced `REVERSAL` transaction and preserves projection equality.
- Authorization cancellation is idempotent on retry and releases the hold.
- Lost-card reporting changes card status to `LOST`; later authorization is rejected.
- Account projections equal signed ledger postings, available balance is non-negative, and all card-related transactions remain balanced.
- Card commands append `CARD_*` audit events.

## Remaining Risk

This slice proves the first synthetic card lifecycle path. Future hardening should add partial capture rules, merchant category controls, richer channel/risk policies, Temporal-backed dispute/lost-card replacement workflows, and live browser/Keycloak evidence against a running Spring API.
