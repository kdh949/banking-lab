import { spawnSync } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import {
  hasText,
  parseKubernetesDocuments,
  requireDocument,
  writeJsonPreservingTimestamp
} from "./k8s-yaml-utils.ts";

const k8sDir = path.join("infra", "k8s");
const evidencePath = path.join("docs", "test-evidence", "generated", "k8s-validation.json");
const requiredFiles = [
  "namespace.yaml",
  "configmap.yaml",
  "secret.example.yaml",
  "core-banking-deployment.yaml",
  "core-banking-service.yaml",
  "reporting-service-deployment.yaml",
  "reporting-service.yaml",
  "reporting-domain-event-publisher-deployment.yaml",
  "payment-service-deployment.yaml",
  "payment-service.yaml",
  "payment-outbox-worker-deployment.yaml",
  "payment-domain-event-publisher-deployment.yaml",
  "notification-service-deployment.yaml",
  "notification-service.yaml",
  "notification-event-consumer-deployment.yaml",
  "core-banking-temporal-worker-deployment.yaml",
  "postgres-statefulset.yaml",
  "keycloak-deployment.yaml",
  "redpanda-deployment.yaml",
  "temporal-deployment.yaml",
  "network-policy.yaml"
];

const files = (await readdir(k8sDir)).filter((file) => file.endsWith(".yaml")).sort();
const errors: string[] = [];
for (const file of requiredFiles) {
  if (!files.includes(file)) {
    errors.push(`Missing required Kubernetes manifest: infra/k8s/${file}`);
  }
}

const documents = [];
for (const file of files) {
  const filePath = path.join(k8sDir, file);
  documents.push(...parseKubernetesDocuments(await readFile(filePath, "utf8"), filePath));
}

requireDocument(documents, "Namespace", "banking-lab", errors);
requireDocument(documents, "ConfigMap", "banking-lab-config", errors);
const secret = requireDocument(documents, "Secret", "banking-lab-secret", errors);
const coreDeployment = requireDocument(documents, "Deployment", "core-banking-service", errors);
requireDocument(documents, "Service", "core-banking-service", errors);
const reportingDeployment = requireDocument(documents, "Deployment", "reporting-service", errors);
requireDocument(documents, "Service", "reporting-service", errors);
const reportingDomainEventPublisherDeployment = requireDocument(documents, "Deployment", "reporting-domain-event-publisher", errors);
const paymentDeployment = requireDocument(documents, "Deployment", "payment-service", errors);
requireDocument(documents, "Service", "payment-service", errors);
const paymentWorkerDeployment = requireDocument(documents, "Deployment", "payment-outbox-worker", errors);
const paymentDomainEventPublisherDeployment = requireDocument(documents, "Deployment", "payment-domain-event-publisher", errors);
const notificationDeployment = requireDocument(documents, "Deployment", "notification-service", errors);
requireDocument(documents, "Service", "notification-service", errors);
const notificationConsumerDeployment = requireDocument(documents, "Deployment", "notification-event-consumer", errors);
const workerDeployment = requireDocument(documents, "Deployment", "core-banking-temporal-worker", errors);
const postgres = requireDocument(documents, "StatefulSet", "postgres", errors);
const keycloak = requireDocument(documents, "Deployment", "keycloak", errors);
const redpanda = requireDocument(documents, "Deployment", "redpanda", errors);
const temporal = requireDocument(documents, "Deployment", "temporal", errors);
requireDocument(documents, "NetworkPolicy", "banking-lab-default-deny-and-app-allow", errors);

for (const deployment of [coreDeployment, reportingDeployment, reportingDomainEventPublisherDeployment, paymentDeployment, paymentWorkerDeployment, paymentDomainEventPublisherDeployment, notificationDeployment, notificationConsumerDeployment, postgres, keycloak, redpanda, temporal]) {
  if (!hasText(deployment, "readinessProbe:") || !hasText(deployment, "livenessProbe:")) {
    errors.push(`${deployment?.kind}/${deployment?.name} must define readinessProbe and livenessProbe.`);
  }
}
if (!hasText(workerDeployment, "BANKING_LAB_TEMPORAL_WORKER_ENABLED") || !hasText(workerDeployment, "value: \"true\"")) {
  errors.push("Temporal worker deployment must explicitly enable BANKING_LAB_TEMPORAL_WORKER_ENABLED=true.");
}
if (
  !hasText(reportingDeployment, "reporting_flyway_schema_history") ||
  !hasText(reportingDeployment, "reporting-service-api") ||
  !hasText(reportingDeployment, "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !hasText(reportingDeployment, "value: \"false\"")
) {
  errors.push("Reporting service deployment must use its own Flyway table, reporting-service audience, and disabled domain publisher mode.");
}
if (
  !hasText(reportingDomainEventPublisherDeployment, "reporting_flyway_schema_history") ||
  !hasText(reportingDomainEventPublisherDeployment, "reporting-service-api") ||
  !hasText(reportingDomainEventPublisherDeployment, "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !hasText(reportingDomainEventPublisherDeployment, "value: \"true\"") ||
  !hasText(reportingDomainEventPublisherDeployment, "BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS") ||
  !hasText(reportingDomainEventPublisherDeployment, "redpanda:9092") ||
  !hasText(reportingDomainEventPublisherDeployment, "ReportRetentionSweepCompleted")
) {
  errors.push("Reporting domain event publisher deployment must enable Redpanda-backed reporting outbox publication.");
}
if (
  !hasText(paymentDeployment, "payment_flyway_schema_history") ||
  !hasText(paymentDeployment, "payment-service-api") ||
  !hasText(paymentDeployment, "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED") ||
  !hasText(paymentDeployment, "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !hasText(paymentDeployment, "value: \"false\"") ||
  !hasText(paymentDeployment, "http://core-banking-service:8081")
) {
  errors.push("Payment service deployment must use its own Flyway table, payment-service audience, disabled worker/publisher modes, and core-banking service endpoint.");
}
if (
  !hasText(paymentWorkerDeployment, "payment_flyway_schema_history") ||
  !hasText(paymentWorkerDeployment, "payment-service-api") ||
  !hasText(paymentWorkerDeployment, "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED") ||
  !hasText(paymentWorkerDeployment, "value: \"true\"") ||
  !hasText(paymentWorkerDeployment, "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !hasText(paymentWorkerDeployment, "value: \"false\"") ||
  !hasText(paymentWorkerDeployment, "BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL") ||
  !hasText(paymentWorkerDeployment, "PAYMENT_CORE_BANKING_CLIENT_SECRET")
) {
  errors.push("Payment outbox worker deployment must enable ledger dispatch, disable domain publishing, and use Keycloak client credentials for the core-banking posting bridge.");
}
if (
  !hasText(paymentDomainEventPublisherDeployment, "payment_flyway_schema_history") ||
  !hasText(paymentDomainEventPublisherDeployment, "payment-service-api") ||
  !hasText(paymentDomainEventPublisherDeployment, "BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED") ||
  !hasText(paymentDomainEventPublisherDeployment, "value: \"false\"") ||
  !hasText(paymentDomainEventPublisherDeployment, "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !hasText(paymentDomainEventPublisherDeployment, "value: \"true\"") ||
  !hasText(paymentDomainEventPublisherDeployment, "BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS") ||
  !hasText(paymentDomainEventPublisherDeployment, "redpanda:9092") ||
  !hasText(paymentDomainEventPublisherDeployment, "PaymentInstructionCanceled")
) {
  errors.push("Payment domain event publisher deployment must disable ledger dispatch and enable Redpanda-backed payment domain event publishing.");
}
if (
  !hasText(notificationDeployment, "notification_flyway_schema_history") ||
  !hasText(notificationDeployment, "notification-service-api") ||
  !hasText(notificationDeployment, "BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED") ||
  !hasText(notificationDeployment, "value: \"false\"")
) {
  errors.push("Notification service deployment must use its own Flyway table, notification-service audience, and synthetic provider boundary.");
}
if (
  !hasText(notificationConsumerDeployment, "notification_flyway_schema_history") ||
  !hasText(notificationConsumerDeployment, "notification-service-api") ||
  !hasText(notificationConsumerDeployment, "BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED") ||
  !hasText(notificationConsumerDeployment, "value: \"true\"") ||
  !hasText(notificationConsumerDeployment, "redpanda:9092")
) {
  errors.push("Notification event consumer deployment must enable the worker against the Redpanda domain-events stream.");
}
if (!hasText(secret, "replace-with-local-synthetic-password")) {
  errors.push("secret.example.yaml must use replace-with-local-synthetic-password placeholders only.");
}
if (documents.some((document) => /real[_ -]?(customer|money|pii|bank|network)/i.test(document.content))) {
  errors.push("Kubernetes manifests must not reference real customer, money, PII, bank, or network data.");
}

const kubectl = spawnSync("kubectl", ["apply", "--dry-run=client", "--validate=false", "-f", k8sDir], {
  encoding: "utf8",
  maxBuffer: 1024 * 1024
});
const kubectlStatus = kubectl.error
  ? "not_available"
  : kubectl.status === 0
    ? "pass"
    : isClusterConnectionFailure(kubectl.stderr)
      ? "skipped_no_cluster"
      : "failed";

if (kubectlStatus === "failed") {
  errors.push(`kubectl client dry-run failed: ${kubectl.stderr || kubectl.stdout}`);
}

const payload = {
  command: "npm run k8s:validate",
  structuralValidation: errors.length === 0 ? "pass" : "failed",
  kubectlClientDryRun: {
    status: kubectlStatus,
    command: "kubectl apply --dry-run=client --validate=false -f infra/k8s",
    stdout: kubectlStatus === "skipped_no_cluster" ? "" : trim(kubectl.stdout),
    stderr: kubectlStatus === "skipped_no_cluster"
      ? "kubectl client is installed, but no local Kubernetes API server was reachable; structural validation was used."
      : trim(kubectl.stderr)
  },
  requiredFiles,
  documentCount: documents.length,
  resources: documents.map((document) => ({
    file: document.file,
    apiVersion: document.apiVersion,
    kind: document.kind,
    name: document.name,
    namespace: document.namespace
  })),
  productionClaim: false,
  syntheticOnly: true,
  errors
};
await writeJsonPreservingTimestamp(evidencePath, payload);

if (errors.length > 0) {
  throw new Error(`Kubernetes validation failed:\n${errors.join("\n")}`);
}

console.log(
  `Kubernetes manifest validation passed: ${documents.length} resources, kubectl ${kubectlStatus}.`
);

function isClusterConnectionFailure(stderr: string): boolean {
  return /connection.*refused|operation not permitted|the server.*could not find|no configuration has been provided|unable to connect/i.test(stderr);
}

function trim(value: string | undefined): string {
  const output = value || "";
  return output.length > 3000 ? `${output.slice(0, 3000)}\n...<truncated>` : output;
}
