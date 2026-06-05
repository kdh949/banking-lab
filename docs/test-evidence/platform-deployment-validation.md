# Platform Deployment Validation Evidence

Date: 2026-06-05

## Commands

```bash
npm run k8s:validate
npm run helm:template
kind create cluster --name banking-lab-evidence --wait 120s
docker build -f infra/docker-compose/core-banking.Dockerfile -t banking-lab/core-banking-service:local .
kind load docker-image banking-lab/core-banking-service:local --name banking-lab-evidence
helm install banking-lab infra/helm/banking-lab --namespace banking-lab --create-namespace --set temporalWorker.replicas=0 --wait --timeout 5m
kubectl get --raw /api/v1/namespaces/banking-lab/services/http:banking-lab-core-banking:8081/proxy/health
kubectl apply --server-side --dry-run=server --force-conflicts -f infra/k8s
helm uninstall banking-lab -n banking-lab --wait
kind delete cluster --name banking-lab-evidence
```

## Result

Structural, template, and disposable live-cluster commands passed.

- `npm run k8s:validate` checked 19 required `infra/k8s` files and 23 Kubernetes resources. The current rerun had no reachable local cluster, so `docs/test-evidence/generated/k8s-validation.json` records `kubectlClientDryRun.status = "skipped_no_cluster"` while structural validation passed.
- `npm run helm:template` used the local Helm CLI and validated 17 rendered resources.
- The disposable kind cluster `banking-lab-evidence` was created and deleted in this evidence run.
- `helm install ... --set temporalWorker.replicas=0 --wait --timeout 5m` installed the chart successfully. The live scope was PostgreSQL plus the core-banking Spring service; the Temporal worker was scaled to zero because this chart does not deploy a Temporal server.
- `banking-lab-core-banking` and `banking-lab-postgres` both reached `1/1` Ready.
- The in-cluster service smoke through the Kubernetes API proxy returned `{"status":"ok","syntheticOnly":true,"auditHashChainValid":true,"nodeReferenceRuntimeRetained":true,"migrationTarget":"kotlin-spring-boot"}`.
- Raw `infra/k8s` manifests passed live API server dry-run with `kubectl apply --server-side --dry-run=server --force-conflicts -f infra/k8s`. The first dry-run without `--force-conflicts` reported Secret field-manager conflicts because Helm already owned the live release Secret fields.

Generated evidence:

- `docs/test-evidence/generated/k8s-validation.json`
- `docs/test-evidence/generated/helm-template-validation.json`
- `docs/test-evidence/generated/k8s-live-deployment-validation.json`

## Scope

This is a synthetic lab deployment skeleton, not production deployment proof. The manifests include namespace, config, example-only secrets, core banking service, payment service, payment outbox worker, reporting service, notification service, notification event-consumer worker, Temporal worker, PostgreSQL, Keycloak, Redpanda, Temporal, and network policy intent. Secrets are placeholders only and must not contain real credentials.

## Remaining Risk

This run proves structural Kubernetes/Helm coverage for PostgreSQL, core banking, payment service, payment outbox worker, reporting service, notification service, notification event-consumer worker, platform dependencies, and network policy intent. The older disposable live Helm install/readiness/smoke evidence still covers PostgreSQL plus core banking only. It does not prove ingress, TLS, production secret manager, Argo CD sync, canary promotion, multi-node storage behavior, payment-service live rollout, payment worker dispatch, reporting-service live rollout, notification-service live rollout, notification worker consumption in-cluster, or a full live Temporal/Redpanda/Keycloak runtime. A first `./gradlew :services:core-banking:bootJar` attempt failed under the default JDK 26.0.1, but `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :services:core-banking:bootJar` passed before the Docker image was rebuilt and deployed.
