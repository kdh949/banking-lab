# Hosted CI Run Status

Review date: 2026-06-10

Run status: blocked

This document records hosted GitHub Actions status separately from local
fallback evidence. It does not claim hosted CI is green.

## Latest Hosted Runs Checked

| Run | Event | Commit | Status | Result | URL |
| --- | --- | --- | --- | --- | --- |
| CI | push to `main` after PR #57 | `9b383b9baf3224f7c65530397b60f018644205ff` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269207643 |
| CI | pull_request #57 | `f881ba228d774c4f1bd536d799e25416a309f87a` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27269197811 |
| CI | push to `main` after PR #56 | `8b11d45cf020cd6b4c1e9494ad235bdd8cec6686` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27268958633 |
| CI | pull_request #56 | `8c1639e41ca62f4b578c879fb9f6b353a6f041f2` | completed | blocked before runner steps | https://github.com/kdh949/banking-lab/actions/runs/27268950008 |

## Failure Classification

The latest main push run (`27269207643`) reported every job as failed within a
few seconds, with an empty `steps` array for each job. The `Node reference and
manifests` check annotation says:

```text
The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings
```

This is an external hosted-runner availability/billing block. It is not a
passing hosted CI run and should not be represented as green.

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
