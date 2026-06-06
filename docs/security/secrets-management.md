# Secrets Management

This repository is a synthetic banking lab. Do not commit real customer data, real PII, real payment-network credentials, real KYC provider credentials, production bank secrets, private keys, or reusable tokens.

## Local Development

- Use `.env.example` as the committed template.
- Put local overrides in `.env`; `.env` and `.env.*` are ignored.
- The default `banking_lab` password is allowed only for disposable local Compose/Testcontainers paths.
- Simulator tokens are local-only and must stay disabled for prod-like profiles.

## Kubernetes And Helm

- Commit example `Secret` manifests only when every value is a placeholder such as `replace-with-local-synthetic-password`.
- Do not commit rendered real Secret manifests.
- Production-like secret material must come from an external secret manager or private CI/runtime injection, not this repository.

## Guardrails

- `npm run security:secrets-check` scans committed configuration, scripts, docs, service code, and platform manifests for private keys, real-looking tokens, and non-placeholder secret assignments.
- Core banking fails startup for prod-like profiles (`prod`, `production`, `prod-like`, `prodlike`) when `spring.datasource.password` is blank, `banking_lab`, `admin`, `password`, or `replace-with-local-synthetic-password`.
- Existing simulator-token guards fail startup for prod-like profiles when simulator tokens are enabled.

## Rotation Procedure

1. Create replacement synthetic/local secret material outside git.
2. Inject it through environment variables, Kubernetes Secrets generated outside this repo, or the local shell.
3. Restart the affected synthetic service.
4. Run `npm run security:secrets-check`.
5. For core banking, run the smallest affected Spring test or smoke command.
6. Record command output and any skipped live-runtime checks in `docs/test-evidence/`.

## Incident Response

- If a real credential is committed, treat it as exposed.
- Revoke or rotate it outside this lab before continuing work.
- Remove the value from git history using an approved repository process.
- Add a scanner regression case if the pattern was not already blocked.
