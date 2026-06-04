# Governance Artifact Mapping

This mapping is scoped to the current synthetic banking-lab controls. It is not a certification, regulator submission, production audit pack, or real financial-institution operating procedure.

| Governance area | Implemented control | Automated artifact |
| --- | --- | --- |
| Access-rights review | `scripts/run-governance-evidence.ts` reads `infra/keycloak/realm-banking-lab.json`, lists every synthetic principal and role, records `lastReviewedAt`, identifies critical roles, and flags over-privilege combinations for review | `docs/test-evidence/generated/governance/access-rights-review.json` |
| Deployment approval | The H1-H8 synthetic release has a change record, branch/commit reference, release scope, separated maker/checker approval, and required pre-release commands | `docs/test-evidence/generated/governance/deployment-approval-evidence.json` |
| Incident response | The synthetic runbook ties detection, triage, containment, recovery, verification, and closure to H4 HA/DR and live PostgreSQL restore evidence | `docs/incident-response/synthetic-incident-response-runbook.md`, `docs/test-evidence/generated/governance/incident-response-drill-log.json` |
| Vulnerability remediation | `security-evidence-summary.json` SCA/SAST/Trivy/SBOM/DAST checks are converted into owner, status-history, and remediation-status records | `docs/test-evidence/generated/governance/vulnerability-remediation-tracker.json` |
| Evidence refresh | H8 artifacts are checked by `npm run evidence:refresh-check` and summarized in the H8 evidence document | `docs/test-evidence/generated/governance/governance-evidence-summary.json`, `docs/test-evidence/hardening-h8-governance-artifacts.md` |

## Synthetic Boundary

The H8 artifacts are generated from checked-in synthetic realm data and previously generated lab evidence. They do not use real customer data, real PII, real KYC, real sanctions data, real payment-network data, external financial-institution APIs, enterprise IAM, ticketing systems, deployment controllers, SIEM systems, or regulator filing channels.
