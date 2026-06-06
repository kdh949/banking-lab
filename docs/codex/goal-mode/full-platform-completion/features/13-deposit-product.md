# Deposit Product

## Goal

Manage synthetic deposit and savings products, product conditions, account
enrollment, interest rate versions, maturity, termination, and product parameter
approval.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/product/**`
- `screen-manifests/staff-terminal/PRD-*.json`
- `screen-manifests/ops-console/OPS-401.interest-accrual-run.json`
- `db/migrations/**`
- `docs/implementation-coverage-matrix.md`

## Target Folder Placement

Keep product rules that affect account opening, interest, fees, and ledger
posting in `services/core-banking/.../product`.

## Backend Implementation Plan

- Implement product catalog, eligibility, product enrollment, product status,
  rate versions, maturity rules, early termination rules, and parameter changes.
- Link accounts to product versions so historical interest/fee calculations are
  reproducible.
- Route rate changes through maker-checker.

## Database / Migration Plan

Use deposit products, product versions, product enrollments, rate versions,
scheduled rate changes, maturity rules, termination records, and approval links.

## API / Event / Workflow Contracts

Expose product list, detail, enrollment, rate change request/approval, maturity,
and termination APIs. Emit product version and enrollment events.

## Frontend / Screen Manifest Plan

Use `PRD-101` for product list and `PRD-102` for rate change requests. Customer
web may show enrolled product terms.

## Security, Audit, Maker-Checker Controls

Product parameter changes are high-risk operational controls and require
maker-checker. Product reads can be lower risk but still need role policy in
staff/admin contexts.

## Tests And Evidence

Run product integration tests, fee/interest tests, manifest validation, and
ops/staff typechecks. Evidence must show versioned rates and approved changes.

## Acceptance Criteria

- Product definitions are durable and versioned.
- Account enrollment references product version.
- Rate changes are approved and auditable.
- Interest and fee engines use product terms.

## Explicit Non-Goals

No real deposit product sale, no regulatory disclosure automation, and no real
pricing engine integration.

