# Platform Deployment Validation Evidence

Date: 2026-06-05

## Commands

```bash
npm run platform:validate
npm run k8s:validate
npm run helm:template
npm run argocd:validate
npm run platform:live-runtime-smoke
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

- `npm run platform:validate` ran the Kubernetes, Helm, and Argo CD structural gates together.
- `npm run k8s:validate` checked 23 required `infra/k8s` files and 27 Kubernetes resources, including TLS Ingress and an example-only TLS secret placeholder. The current rerun had no reachable local cluster, so `docs/test-evidence/generated/k8s-validation.json` records `kubectlClientDryRun.status = "skipped_no_cluster"` while structural validation passed.
- `npm run helm:template` used the local Helm CLI and validated 27 rendered resources, including Helm-managed Keycloak, Redpanda, Temporal, TLS Ingress, and synthetic TLS secret placeholder resources.
- `npm run argocd:validate` validated both `infra/argocd` Application manifests for the synthetic Helm chart path, in-cluster destination, disabled automated prune, self-heal, namespace creation, and no embedded credential material.
- `npm run platform:live-runtime-smoke` is now the repeatable disposable kind smoke for the scoped live runtime slice. It builds and loads the local core-banking image, installs the Helm chart with payment/reporting/notification application replicas and workers scaled to zero, waits for PostgreSQL, Keycloak, Redpanda, Temporal, and core-banking readiness, checks core `/health` through the Kubernetes service proxy, checks Keycloak management readiness on port 9000, checks Redpanda admin readiness, verifies Temporal service endpoints, verifies the TLS Ingress object and synthetic TLS Secret type, records Argo CD sync as skipped when no Argo CD controller/CRD is present, and writes `docs/test-evidence/generated/platform-live-runtime-smoke.json`.
- The disposable kind cluster `banking-lab-evidence` was created and deleted in this evidence run.
- `helm install ... --set temporalWorker.replicas=0 --wait --timeout 5m` installed the chart successfully. The live scope was PostgreSQL plus the core-banking Spring service; the Temporal worker was scaled to zero because this chart does not deploy a Temporal server.
- `banking-lab-core-banking` and `banking-lab-postgres` both reached `1/1` Ready.
- The in-cluster service smoke through the Kubernetes API proxy returned `{"status":"ok","syntheticOnly":true,"auditHashChainValid":true,"nodeReferenceRuntimeRetained":true,"migrationTarget":"kotlin-spring-boot"}`.
- Raw `infra/k8s` manifests passed live API server dry-run with `kubectl apply --server-side --dry-run=server --force-conflicts -f infra/k8s`. The first dry-run without `--force-conflicts` reported Secret field-manager conflicts because Helm already owned the live release Secret fields.

Generated evidence:

- `docs/test-evidence/generated/k8s-validation.json`
- `docs/test-evidence/generated/helm-template-validation.json`
- `docs/test-evidence/generated/argocd-validation.json`
- `docs/test-evidence/generated/k8s-live-deployment-validation.json`
- `docs/test-evidence/generated/platform-live-runtime-smoke.json`

## Scope

This is a synthetic lab deployment skeleton, not production deployment proof. The manifests include namespace, config, example-only secrets, TLS Ingress with synthetic certificate placeholders, core banking service, payment service, payment outbox worker, payment domain-event publisher, reporting service, reporting domain-event publisher, notification service, notification event-consumer worker, Temporal worker, PostgreSQL, Keycloak, Redpanda, Temporal, Argo CD Application definitions, and network policy intent. Secrets are placeholders only and must not contain real credentials.

## Remaining Risk

This run proves structural Kubernetes/Helm/Argo CD coverage for PostgreSQL, core banking, payment service, payment outbox worker, payment domain-event publisher, reporting service, reporting domain-event publisher, notification service, notification event-consumer worker, Keycloak, Redpanda, Temporal, TLS Ingress, synthetic secret placeholders, and network policy intent. The repeatable disposable live runtime smoke now covers PostgreSQL, Keycloak, Redpanda, Temporal, core-banking health, TLS Ingress object presence, and synthetic TLS Secret presence in a kind cluster. It does not prove a live ingress controller traffic path, real TLS termination, production secret manager, Argo CD sync health without an installed Argo CD controller, canary promotion, multi-node storage behavior, payment-service live rollout, payment worker dispatch, payment domain-event publisher polling, reporting-service live rollout, reporting domain-event publisher polling, notification-service live rollout, or notification worker consumption in-cluster. A first `./gradlew :services:core-banking:bootJar` attempt failed under the default JDK 26.0.1, but `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :services:core-banking:bootJar` passed before the Docker image was rebuilt and deployed.
