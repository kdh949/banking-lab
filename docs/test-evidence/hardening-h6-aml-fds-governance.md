# Hardening H6 AML/FDS Governance Evidence

Date: 2026-06-05

## Scope

H6 adds AML/FDS depth for the synthetic lab only:

- synthetic sanctions and PEP watchlist entries;
- customer and transfer-counterparty screening that opens AML cases for hits;
- false-positive disposition with reviewer/checker separation and compliance-manager approval role validation;
- AML/FDS model-card metadata tied to the deterministic DuckDB analytics evidence;
- generated synthetic STR and periodic AML regulatory-report artifacts.

This slice does not load real sanctions, PEP, KYC, customer PII, payment-network, regulator, or external financial-institution data. Generated STR and regulatory artifacts are in-repo evidence only and are not submitted anywhere.

## Changed Control Surface

- New migration: `db/migrations/V029__aml_fds_governance_reporting.sql`.
- New API surface:
  - `POST /api/aml/governance/screen/customers/{customerId}`;
  - `POST /api/aml/governance/screen/transfers`;
  - `POST /api/aml/governance/hits/{hitId}/false-positive-dispositions`;
  - `GET /api/aml/governance/model-card`.
- `/api/aml/governance/*` is restricted to AML, compliance, and auditor roles; POST commands require fresh step-up.
- New command: `npm run aml:str-report`.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run scripts:typecheck` | first run failed on `scripts/run-aml-str-report.ts` generic `reduce` typing; rerun passed after replacing it with an explicit typed loop |
| `node --test tests/amlGovernance.test.mjs` | pass; runs `npm run aml:str-report` and validates generated artifacts |
| `npm run test:core-banking:integration -- --tests lab.banking.core.aml.AmlFdsGovernanceIntegrationTest --rerun-tasks` | first sandbox run failed on Gradle file-lock socket; escalated rerun exposed an AML alert DTO mismatch; final rerun passed after emitting existing `{ruleId,message}` alert JSON |

## Generated Evidence

| Artifact | Evidence |
| --- | --- |
| Model card | `docs/test-evidence/generated/aml-model-card.json` |
| Synthetic STR | `docs/test-evidence/generated/synthetic-str-report.json` |
| Periodic AML report | `docs/test-evidence/generated/aml-regulatory-report.json` |
| Summary | `docs/test-evidence/generated/aml-str-report-summary.json` |

## Evidence Summary

| Control | Evidence |
| --- | --- |
| Synthetic sanctions and PEP screening | `AmlFdsGovernanceIntegrationTest` flags `Synthetic Sanction Match` and `Synthetic PEP Match` from seeded synthetic watchlist rows |
| Step-up for high-risk AML commands | same test rejects customer screening without fresh step-up using `STEP_UP_REQUIRED` |
| False-positive disposition | same test rejects self-approval with `MAKER_CHECKER_SELF_APPROVAL_REJECTED`, rejects a non-compliance checker role, and records a valid disposition by an independent compliance actor |
| Model governance | `GET /api/aml/governance/model-card` returns active `AML-MODEL-SYN-RULES-V1`; `npm run aml:str-report` writes model-card lineage against `fds-aml-analytics.json` |
| STR/regulatory reporting | `npm run aml:str-report` writes synthetic-only STR and periodic AML report artifacts with no real regulator submission |
| Audit | integration test verifies `SANCTIONS_SCREENING_HIT` and `SANCTIONS_FALSE_POSITIVE_DISPOSITIONED` audit rows |

## Remaining Limitations

- This is deterministic local simulation, not integration with a real sanctions provider, regulator, FIU, or payment network.
- The model-card artifact is generated from existing synthetic DuckDB analytics evidence; it is not a production model-risk-management workflow.
- Report artifacts are JSON evidence files, not signed filings or external submissions.
