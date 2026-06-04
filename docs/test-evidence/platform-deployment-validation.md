# Platform Deployment Validation Evidence

Date: 2026-06-04

## Commands

```bash
npm run k8s:validate
npm run helm:template
```

## Result

Both commands passed.

- `npm run k8s:validate` checked 11 required `infra/k8s` files and 15 Kubernetes resources. `kubectl` was installed locally, but no local Kubernetes API server was reachable, so the command recorded `kubectlClientDryRun.status = "skipped_no_cluster"` and relied on structural manifest validation.
- `npm run helm:template` found no local `helm` binary, so it used the Node structural renderer for this chart's limited template subset and validated 9 rendered resources.

Generated evidence:

- `docs/test-evidence/generated/k8s-validation.json`
- `docs/test-evidence/generated/helm-template-validation.json`

## Scope

This is a synthetic lab deployment skeleton, not production deployment proof. The manifests include namespace, config, example-only secrets, core banking service, Temporal worker, PostgreSQL, Keycloak, Redpanda, Temporal, and network policy intent. Secrets are placeholders only and must not contain real credentials.

## Remaining Risk

No cluster apply, live Helm release, ingress, TLS, production secret manager, persistent storage class, or runtime canary was executed in this local evidence run.
