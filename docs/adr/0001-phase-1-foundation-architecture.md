# ADR 0001: Phase 1 Foundation Architecture

## Status

Accepted

## Context

The first milestone needs a runnable banking lab skeleton without real money, real PII, or real payment networks. The highest-risk future work is ledger integrity, auditability, maker-checker control, and screen count growth.

## Decision

Use a no-dependency Node runtime for Phase 1. Keep the core domain in reusable packages and expose app shells through a single local server.

Foundation boundaries:

- `packages/banking-domain` owns double-entry transaction creation, balance projection, idempotency, reversal, audit hash chain, masking, maker-checker, workflow, and synthetic data.
- `packages/screen-engine` validates screen manifests before runtime use.
- `runtime` serves static app shells and mock APIs backed by in-memory state.
- `infra/db/migrations/001_foundation.sql` defines the intended PostgreSQL schema and append-only guards for later persistence.
- `screen-manifests` are the source of business-screen metadata.

## Consequences

Positive:

- The project can run and test without dependency download or external services.
- Foundation invariants are executable from the first commit.
- Screen growth starts from manifests instead of one-off views.

Tradeoffs:

- Runtime state is in-memory until the database layer is wired.
- Mock auth replaces Keycloak for Phase 1.
- Docker Compose requires a local or pullable Node image.

## Follow-up

- Add a real persistence adapter for PostgreSQL and execute migrations in integration tests.
- Replace mock auth with Keycloak or a local OIDC simulator.
- Expand the workflow engine beyond complaint transitions.
