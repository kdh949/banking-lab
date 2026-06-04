# Hardening H5 Operational-Security Lab Evidence

Date: 2026-06-05

## Scope

H5 adds production-shaped operational-security controls as synthetic lab simulators:

- WORM-style audit export metadata through `audit_worm_export_segments`;
- synthetic KMS/HSM key versions through `synthetic_kms_keys`;
- PAM break-glass grants with mandatory independent post-hoc review cases;
- Loki SIEM alert rules for failed-auth bursts, privilege changes, mass PII access, and break-glass usage;
- a documented Keycloak/DB secret rotation runbook that uses environment/Kubernetes-secret patterns and commits no real secret material.

This evidence uses only synthetic audit events, synthetic key hashes, synthetic operators, and local alert simulation. It does not use real money, real PII, real KYC, real payment networks, real sanctions data, real external security providers, or real financial institution APIs.

## Changed Control Surface

- New migration: `db/migrations/V028__operational_security_lab_controls.sql`.
- New API surface: `/api/ops/security/audit-exports`, `/api/ops/security/audit-exports/verification`, `/api/ops/security/kms/rotate`, and `/api/ops/security/break-glass/*`.
- `/api/ops/security/*` is restricted to `OPS_MANAGER`, `COMPLIANCE_MANAGER`, and `AUDITOR`; POST commands require fresh step-up.
- New Loki rules: `infra/observability/loki/rules/fake/operational-security-alerts.yml`.
- New command: `npm run siem:alert-drill`.

## Commands Run

| Command | Result |
| --- | --- |
| `npm run siem:alert-drill` | pass; generated `docs/test-evidence/generated/siem-alert-drill.json` |
| `node --test tests/operationalSecurity.test.mjs` | pass |
| `npm run test:core-banking:integration -- --tests lab.banking.core.opsec.OperationalSecurityIntegrationTest --rerun-tasks` | first sandbox run failed on Gradle file-lock socket; escalated rerun initially exposed disabled step-up in the test context; final rerun passed after setting `banking-lab.security.step-up.enforcement-enabled=true` for the test |
| `npm run test:core-banking:integration -- --rerun-tasks` | pass |
| `npm test` | pass; 143 tests |
| `npm run validate:manifests` | pass; 87 manifests |
| `npm run test:screen-engine` | pass; 10 tests |
| `npm run node:retirement-gate` | pass |

## Evidence Summary

| Control | Evidence |
| --- | --- |
| WORM audit export | `OperationalSecurityIntegrationTest` exports a sealed segment and verifies its hash anchor |
| Tamper detection | test mutates a sealed segment hash and verification returns `valid=false` |
| KMS/HSM-sim rotation | test rotates `AUDIT_WORM_ANCHOR` from v1 to v2 and old segment verification remains valid |
| PAM break-glass | test creates a time-boxed grant, expires it, rejects self-review, and closes review by a different compliance actor |
| SIEM alert drill | `npm run siem:alert-drill` verifies four synthetic Loki alert rules and writes generated evidence |

## Remaining Limitations

- The SIEM drill is deterministic local rule simulation, not a live Loki ingestion run.
- Secret rotation is documented and wired to env/secret patterns; it does not rotate live production credentials.
- Break-glass is a lab-grade state machine; it does not grant real IAM permissions or real infrastructure access.
