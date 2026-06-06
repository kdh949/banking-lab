# Reporting Service

## Goal

Provide synthetic operational, audit, statement, evidence, and management reports
without weakening ledger source-of-truth or exposing unmasked data by default.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/statement/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/admin/**`
- `apps/audit-console/src/**`
- `apps/admin-console/src/**`
- `docs/test-evidence/**`
- `docs/implementation-coverage-matrix.md`

## Target Folder Placement

Keep lightweight statement/certificate read models in `services/core-banking`.
Create `services/reporting-service` for heavier reports, scheduled report
generation, export simulation, and management/evidence reporting.

## Implementation Plan

- Define report categories: customer statements, balance certificates, audit
  reports, operational coverage, security evidence, reconciliation, FDS/AML, and
  platform health.
- Read from ledger projections, audit tables, evidence files, and reporting
  marts rather than mutating business state.
- Apply masking and reason-required access.
- Persist report artifact metadata and source references.

## Tests And Evidence

Test report generation from source data, masking, authorization, artifact source
references, and evidence links. Run relevant console typechecks and evidence
refresh.

## Acceptance Criteria

- Reports are reproducible from target-stack sources.
- Sensitive data is masked by default.
- Report access is audited.
- Heavy reporting does not hold ledger command transactions.

## Explicit Non-Goals

No real regulatory report, no production customer statement, and no external
document delivery.
