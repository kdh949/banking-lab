# CI Evidence

Date: 2026-06-04

## Workflow

`.github/workflows/ci.yml` now defines:

- `node-and-manifests`
- `next-builds`
- `backend-core-banking`
- `playwright-manifest-e2e`
- `security-evidence`
- `formal-model`

Triggers:

- `pull_request`
- `push` to `main`
- `workflow_dispatch`

Permissions default to `contents: read`.

## Notes

The security job runs `npm audit --audit-level=high` and `npm run security:evidence`. Tool-specific skips from Semgrep, Trivy, SBOM, or DAST are recorded by the wrapper and uploaded as artifacts; they are not described as completed security scans unless the tools actually ran.

The formal job runs `npm run formal:ledger`. If a TLC runner is available, generated evidence must record the actual TLC result for both root models. If TLC is unavailable, generated evidence must still show the executable bounded state-search checker passing with `staticOnly: false`; static-only mode is not accepted in CI.
