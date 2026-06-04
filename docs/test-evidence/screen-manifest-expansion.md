# Screen Manifest Expansion Evidence

Date: 2026-06-04

## Result

`npm run validate:manifests` validates 66 manifests.

Counts:

- `admin-console`: 3
- `audit-console`: 3
- `complaint-portal`: 8
- `customer-web`: 10
- `fds-aml-console`: 6
- `ops-console`: 4
- `staff-terminal`: 32

## Tests Added

`packages/screen-engine/test/manifest-parity.test.ts` now asserts:

- total manifest count is at least 60;
- `screenId` values are unique;
- `transactionCode` values are present and unique;
- app-specific minimum counts are satisfied;
- reusable template shapes for `INQUIRY`, `COMMAND`, `CASE`, and `PARAMETER`.

## Control Coverage

The expanded catalog covers inquiry, command, case, parameter, and dashboard screens across customer, staff, complaint, FDS/AML, operations, audit, and admin channels.

High-risk command and parameter screens declare maker-checker approval metadata. Staff PII screens declare reason-required audit metadata and masking policies.
