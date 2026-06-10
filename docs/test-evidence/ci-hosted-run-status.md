# Hosted CI Run Status

Review date: 2026-06-10

Run status: blocked

This document records hosted GitHub Actions status separately from local
fallback evidence. It does not claim hosted CI is green.

## Hosted Runs Checked

| Run | Event | Commit | Status | Result | URL |
| --- | --- | --- | --- | --- | --- |
| CI | pull_request #79 | `dead036460edc5aca402a7ad5017be25ada3b9d5` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27281812788 |
| CI | pull_request #77 | `399de14415cfece78b9584b7b1a36db975ed0f22` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27280452683 |
| CI | pull_request #76 | `9334afafe2812a9d7ed60b596e31bd2932ddd04a` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27279285215 |
| CI | pull_request #75 | `8ef8cf8a74d94371f204bd998aee4ad62a8a94d4` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27279138120 |
| CI | pull_request #74 | `4d362d80a07cee37055747428501a7c81039b2d1` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27278950044 |
| CI | pull_request #59 | `0bd235a557e60acc9380afea955c2b8c6d887736` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269813915 |
| CI | push to `main` after PR #58 | `ccf65285952a9c1bf229773b03661cb797678a76` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269423897 |
| CI | pull_request #58 | `f9efb58643eede62ee0e9ee7c70c98a6d89039c2` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269407681 |
| CI | push to `main` after PR #57 | `9b383b9baf3224f7c65530397b60f018644205ff` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269207643 |
| CI | pull_request #57 | `f881ba228d774c4f1bd536d799e25416a309f87a` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269197811 |
| CI | push to `main` after PR #56 | `8b11d45cf020cd6b4c1e9494ad235bdd8cec6686` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27268958633 |
| CI | pull_request #56 | `8c1639e41ca62f4b578c879fb9f6b353a6f041f2` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27268950008 |

## Failure Classification

The latest checked PR run (`27281812788`) reported every job as failed within a
few seconds, with an empty `steps` array for each job. `gh api
repos/kdh949/banking-lab/actions/jobs/80578094240/logs` returned
`BlobNotFound`, so no hosted command-level failure log exists for that run.

The earlier checked PR run (`27280452683`) reported every job as failed within a
few seconds, with an empty `steps` array for each job. `gh run view
27280452683 --log-failed` returned `log not found`, so no hosted command-level
failure log exists for that run.

The earlier checked PR run (`27278950044`) showed the same empty-steps pattern.
The `Node reference and manifests` check run (`80567736503`) annotation says:

```text
The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings
```

The older checked PR run (`27269813915`) showed the same empty-steps pattern and
the same billing/spending-limit annotation. This is an external hosted-runner
availability/billing block. It is not a passing hosted CI run and should not be
represented as green.

## Jobs Affected

The blocked run affected all workflow jobs:

- `node-and-manifests`
- `next-builds`
- `backend-core-banking`
- `backend-payment-service`
- `backend-notification-service`
- `backend-reporting-service`
- `backend-all-gradle`
- `platform-validation`
- `contracts-validation`
- `compose-platform-config`
- `playwright-manifest-e2e`
- `security-evidence`
- `formal-model`

## Local Fallback Evidence

Local fallback commands are not hosted CI green. They only show the local branch
state before PR/merge.

Local commands run for PR #56:

- `npm test`: pass, 178 tests.
- `npm run validate:manifests`: pass, 67 screen manifests.
- `npm run packages:typecheck`: pass.
- `npm run scripts:typecheck`: pass.

Local commands run for PR #57:

- `node --test tests/coverageMatrixStatus.test.mjs`: pass, 3 tests.
- `npm test`: pass, 181 tests.
- `npm run validate:manifests`: pass, 67 screen manifests.
- `npm run evidence:refresh-check`: pass.

Local commands run for PR #58:

- `npm run ci:check-workflow`: pass.
- `node --test tests/ciHardening.test.mjs`: pass, 2 tests.
- `npm run scripts:typecheck`: pass.
- `npm test`: pass, 181 tests.
- `npm run contracts:lint`: pass.
- `npm run contracts:check-client`: pass.
- `npm run contracts:check-events`: pass.
- `npm run platform:validate`: pass.

Local commands run for PR #59:

- `npm run live-route:evidence`: pass.
- `node --test tests/liveRouteApiEvidence.test.mjs`: pass, 2 tests.
- `npm run scripts:typecheck`: pass.
- `npm run integrated-terminal:boundary-check`: pass.
- `npm test`: pass, 183 tests.

Local commands run for PR #74:

- `node --test tests/finalHardeningBaseline.test.mjs`: pass, 1 test.
- `npm test`: pass, 191 tests.
- `npm run validate:manifests`: pass, 73 screen manifests.
- `npm run packages:typecheck`: pass.
- `npm run scripts:typecheck`: pass.

Local commands run for PR #75:

- `node --test tests/ciHardening.test.mjs`: pass, 2 tests.

Local commands run for PR #76:

- `node --test tests/coverageMatrixStatus.test.mjs`: pass, 3 tests.
- `npm run evidence:refresh-check`: pass.

Local commands run for PR #77:

- `node --test tests/callCenterConsole.test.mjs tests/nextScaffold.test.mjs tests/finalHardeningBaseline.test.mjs tests/evidenceHardening.test.mjs`: pass, 18 tests.
- `node --test tests/coverageMatrixStatus.test.mjs`: pass, 3 tests.
- `npm run next:call-center-console:typecheck`: pass.
- `scripts/run-core-banking-tests.sh :services:core-banking:test --tests lab.banking.core.security.KeycloakRealmPolicyTest`: pass after sandbox escalation.
- `npx playwright test apps/call-center-console/e2e/call-center-console-parity.spec.ts`: pass after sandbox escalation, with 2 passed and 2 skipped because API and Keycloak E2E URLs were not set.

Local commands run for PR #79:

- `node --test tests/callCenterConsole.test.mjs`: pass, 1 test before the live rerun fix.
- `npm run next:call-center-console:typecheck`: pass.
- `npm run test:call-center-console:keycloak-e2e-compose`: first approved run failed in Playwright because the manager Keycloak redirect reset the in-memory agent token; approved rerun passed with 1 live Keycloak browser test after preserving same-tab synthetic token state.
- `node --test tests/callCenterConsole.test.mjs tests/finalHardeningBaseline.test.mjs tests/evidenceHardening.test.mjs tests/coverageMatrixStatus.test.mjs`: pass, 9 tests.
- `git diff --check`: pass.

## Manual Recovery Checklist

1. Resolve the GitHub billing/spending-limit block in repository or account
   settings.
2. Rerun the `CI` workflow through `workflow_dispatch` or by pushing an empty
   safe commit.
3. Record the new run URL, commit SHA, and job conclusions here.
4. Only mark hosted CI green if the GitHub Actions jobs start and complete
   successfully.

## Synthetic Boundary

The CI workflow and local fallback evidence remain synthetic-only. They do not
use real customer money, real PII, real KYC providers, real card networks, real
payment networks, or real financial institution APIs.
