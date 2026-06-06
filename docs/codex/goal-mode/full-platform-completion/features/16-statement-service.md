# Statement Service

## Goal

Generate synthetic transaction histories, monthly statements, transaction
confirmations, balance certificates, account certificates, and audit-backed
read-model evidence from ledger postings.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/statement/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/ledger/**`
- `screen-manifests/customer-web/CWB-103.transaction-history.json`
- `screen-manifests/staff-terminal/LED-102.ledger-transaction-detail.json`
- `docs/test-evidence/statement-read-models.md`

## Target Folder Placement

Keep ledger-backed statement read models in `services/core-banking/.../statement`.
If heavy document rendering is introduced, put it in `services/reporting-service`
and keep core-banking as the source of truth.

## Backend Implementation Plan

- Build transaction history from ledger postings.
- Generate monthly statement read models and certificate artifacts.
- Support transaction confirmation and balance certificate APIs.
- Persist artifact metadata, source ledger range, and audit event references.

## Database / Migration Plan

Use statement artifacts, certificate artifacts, artifact line items or read-model
tables, generation metadata, and ledger source references.

## API / Event / Workflow Contracts

Expose customer-owned statement and certificate APIs plus staff reason-required
certificate reads. Emit statement generated and certificate viewed events.

## Frontend / Screen Manifest Plan

Customer web uses account history and statement/certificate views. Staff terminal
uses ledger detail and certificate inquiry screens.

## Security, Audit, Maker-Checker Controls

Customer reads require ownership. Staff reads require reason. Certificates and
confirmations are audited because they can expose sensitive account state.

## Tests And Evidence

Run statement read-model integration tests, customer/staff typechecks, manifest
validation, ledger invariant tests, and backup/restore evidence where relevant.

## Acceptance Criteria

- Statements derive from ledger postings.
- Certificates are reproducible and audited.
- Customer/staff authorization is enforced.
- Artifacts are synthetic-only.

## Explicit Non-Goals

No legally binding bank statement, no real document delivery, and no production
certificate issuance.
