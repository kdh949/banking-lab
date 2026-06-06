# FDS / AML Analytics

## Goal

Provide synthetic fraud detection and AML analytics using Python, DuckDB or
Spark, rule-based scoring first, optional scikit-learn scoring second, and
Spring/FDS console evidence linkage.

## Current Code To Inspect

- `analytics/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/analytics/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/fds/**`
- `services/core-banking/src/main/kotlin/lab/banking/core/aml/**`
- `apps/fds-aml-console/src/**`
- `screen-manifests/fds-aml-console/**`
- `docs/test-evidence/fds-analytics-spring-read-api.md`
- `docs/test-evidence/fds-aml-reconciliation.md`

## Target Folder Placement

Keep analytical feature generation and scoring in `analytics/**`. Keep the
Spring read API and case decision APIs in `services/core-banking`. Keep screens
in `apps/fds-aml-console` and `screen-manifests/fds-aml-console`.

## Implementation Plan

- Generate synthetic transaction/customer/account features.
- Implement deterministic rule scoring and optional model scoring.
- Persist or export analytics artifacts with synthetic-boundary proof.
- Expose analytics evidence through Spring read APIs with reason-required access.
- Connect high-risk transfer holds and AML cases to staff/FDS/AML workflows.

## Tests And Evidence

Run `npm run analytics:fds-aml:test`, `npm run analytics:fds-aml`,
`FdsAnalyticsEvidenceIntegrationTest`, `npm run next:fds-aml-console:typecheck`,
manifest validation, and evidence refresh.

## Acceptance Criteria

- FDS/AML analytics uses only synthetic data.
- Console reads analytics through API client, not direct file imports.
- Decisions are audited and maker-checker protected where high-risk.
- Evidence artifacts are reproducible.

## Explicit Non-Goals

No real AML screening, sanctions list, customer monitoring, transaction
monitoring provider, or regulatory filing.
