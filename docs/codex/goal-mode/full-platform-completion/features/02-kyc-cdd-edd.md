# KYC / CDD / EDD

## Goal

Model synthetic identity verification, customer due diligence, enhanced due
diligence, risk-grade assignment, periodic review, and staff re-confirmation
without using real KYC providers or real personal data.

## Current Code To Inspect

- `services/core-banking/src/main/kotlin/lab/banking/core/customer/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/staff/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/aml/**`
- `screen-manifests/staff-terminal/KYC-101.customer-kyc-review-request.json`
- `packages/api-client/src/**`
- `db/migrations/**`
- `docs/test-evidence/api-backed-channel-smoke.md`

## Target Folder Placement

Keep KYC profile and review state in
`services/core-banking/src/main/kotlin/lab/banking/core/customer` or a dedicated
`core/kyc` package if the domain grows. Any provider simulation belongs in
`services/external-simulators` or a local synthetic fixture package.

## Backend Implementation Plan

- Persist KYC profile, verification status, risk grade, CDD/EDD level, review
  due date, source type, and synthetic-provider result.
- Support initial KYC before account opening and re-confirmation after risk or
  profile changes.
- Trigger EDD requirements for high-risk synthetic customers or AML flags.
- Keep provider calls simulated and record `realKycProviderCalled=false`.

## Database / Migration Plan

Use tables for `customer_kyc_profiles`, KYC review requests, KYC review history,
and synthetic provider evidence. Link KYC state to customer and account opening
eligibility.

## API / Event / Workflow Contracts

- Staff KYC review request and approval APIs require reason and role checks.
- Account opening checks KYC eligibility.
- Risk-grade changes emit Outbox events for AML/FDS and customer controls.
- Periodic review can be modeled through Temporal-compatible workflow state.

## Frontend / Screen Manifest Plan

Maintain `KYC-101` in staff terminal and expose KYC state in customer 360 views.
Admin parameter screens may control synthetic risk thresholds and review periods.

## Security, Audit, Maker-Checker Controls

KYC data is sensitive. Staff reads require reason; changes require maker-checker;
PII stays masked; provider evidence must prove synthetic-only behavior.

## Tests And Evidence

Test account-opening rejection without valid KYC, KYC review request/approve,
self-approval rejection, unauthorized actor rejection, audit creation, and
synthetic provider boundary. Update evidence after Spring integration tests pass.

## Acceptance Criteria

- KYC/CDD/EDD profile is durable and linked to customer lifecycle.
- Account opening and high-risk operations consult KYC state.
- Review workflow is audited and maker-checker protected.
- No real KYC provider is reachable from runtime config.

## Explicit Non-Goals

No real identity verification, sanctions screening, document capture, biometric
check, or external KYC/AML provider integration.

