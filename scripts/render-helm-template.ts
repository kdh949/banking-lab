import { spawnSync } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import {
  parseKubernetesDocuments,
  scalarFromSection,
  writeJsonPreservingTimestamp
} from "./k8s-yaml-utils.ts";

const chartDir = path.join("infra", "helm", "banking-lab");
const templatesDir = path.join(chartDir, "templates");
const valuesPath = path.join(chartDir, "values.yaml");
const evidencePath = path.join("docs", "test-evidence", "generated", "helm-template-validation.json");
const releaseName = "banking-lab";

const valuesSource = await readFile(valuesPath, "utf8");
const replacements = buildReplacementMap(valuesSource);
const helm = spawnSync("helm", ["template", releaseName, chartDir], {
  encoding: "utf8",
  maxBuffer: 1024 * 1024 * 5
});

let renderer: "helm" | "node-structural";
let rendered: string;
if (!helm.error && helm.status === 0) {
  renderer = "helm";
  rendered = helm.stdout;
} else if (helm.error && (helm.error as NodeJS.ErrnoException).code === "ENOENT") {
  renderer = "node-structural";
  rendered = await renderTemplatesWithNodeFallback();
} else {
  throw new Error(`helm template failed:\n${helm.stderr || helm.stdout}`);
}

const documents = parseKubernetesDocuments(rendered, "helm-rendered-output");
const errors: string[] = [];
for (const expected of [
  "Namespace/banking-lab",
  "ConfigMap/banking-lab-config",
  "Secret/banking-lab-secret",
  "Deployment/banking-lab-core-banking",
  "Service/banking-lab-core-banking",
  "Deployment/banking-lab-reporting-service",
  "Service/banking-lab-reporting-service",
  "Deployment/banking-lab-reporting-domain-event-publisher",
  "Deployment/banking-lab-payment-service",
  "Service/banking-lab-payment-service",
  "Deployment/banking-lab-payment-outbox-worker",
  "Deployment/banking-lab-payment-domain-event-publisher",
  "Deployment/banking-lab-notification-service",
  "Service/banking-lab-notification-service",
  "Deployment/banking-lab-notification-event-consumer",
  "Deployment/banking-lab-temporal-worker",
  "Deployment/banking-lab-keycloak",
  "Service/banking-lab-keycloak",
  "Deployment/banking-lab-redpanda",
  "Service/banking-lab-redpanda",
  "Deployment/banking-lab-temporal",
  "Service/banking-lab-temporal",
  "Secret/banking-lab-ingress-tls",
  "Ingress/banking-lab-ingress",
  "Service/banking-lab-postgres",
  "StatefulSet/banking-lab-postgres",
  "NetworkPolicy/banking-lab-app-allow"
]) {
  const [kind, name] = expected.split("/");
  if (!documents.some((document) => document.kind === kind && document.name === name)) {
    errors.push(`Rendered Helm output missing ${expected}.`);
  }
}
if (!rendered.includes("replace-with-local-synthetic-password")) {
  errors.push("Rendered Helm output must keep synthetic secret placeholders.");
}
if (!rendered.includes("BANKING_LAB_TEMPORAL_WORKER_ENABLED")) {
  errors.push("Rendered Helm output must include the Temporal worker enablement flag.");
}
if (
  !rendered.includes("banking-lab-keycloak") ||
  !rendered.includes("port: 9000") ||
  !rendered.includes("targetPort: management") ||
  !rendered.includes("banking-lab-redpanda") ||
  !rendered.includes("banking-lab-temporal") ||
  !rendered.includes("banking-lab-ingress") ||
  !rendered.includes("secretName: banking-lab-ingress-tls") ||
  !rendered.includes("nginx.ingress.kubernetes.io/ssl-redirect") ||
  !rendered.includes("replace-with-local-synthetic-tls-certificate-pem") ||
  !rendered.includes("replace-with-local-synthetic-tls-private-key-pem")
) {
  errors.push("Rendered Helm output must include Keycloak, Redpanda, Temporal, and TLS Ingress resources with synthetic placeholders.");
}
if (
  !rendered.includes("reporting_flyway_schema_history") ||
  !rendered.includes("reporting-service-api") ||
  !rendered.includes("BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !rendered.includes("BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS") ||
  !rendered.includes("BANKING_LAB_REPORTING_DOMAIN_EVENT_PUBLISHER_DEAD_LETTER_THRESHOLD") ||
  !rendered.includes("banking.lab.reporting-events") ||
  !rendered.includes("ReportRetentionSweepCompleted")
) {
  errors.push("Rendered Helm output must include reporting-service Flyway, audience, and reporting domain publisher settings.");
}
if (
  !rendered.includes("payment_flyway_schema_history") ||
  !rendered.includes("payment-service-api") ||
  !rendered.includes("BANKING_LAB_PAYMENT_OUTBOX_WORKER_ENABLED") ||
  !rendered.includes("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_ENABLED") ||
  !rendered.includes("BANKING_LAB_PAYMENT_DOMAIN_EVENT_PUBLISHER_BOOTSTRAP_SERVERS") ||
  !rendered.includes("BANKING_LAB_PAYMENT_CORE_BANKING_TOKEN_URL") ||
  !rendered.includes("PAYMENT_CORE_BANKING_CLIENT_SECRET") ||
  !rendered.includes("banking.lab.core-banking.ledger-commands") ||
  !rendered.includes("banking.lab.payment-events")
) {
  errors.push("Rendered Helm output must include payment-service worker, domain publisher, Flyway, core-banking token, and audience settings.");
}
if (
  !rendered.includes("notification_flyway_schema_history") ||
  !rendered.includes("notification-service-api") ||
  !rendered.includes("BANKING_LAB_NOTIFICATION_SERVICE_REAL_PROVIDER_ENABLED") ||
  !rendered.includes("BANKING_LAB_NOTIFICATION_EVENT_CONSUMER_ENABLED") ||
  !rendered.includes("banking-lab-redpanda:9092")
) {
  errors.push("Rendered Helm output must include notification-service synthetic provider, consumer, Flyway, and audience settings.");
}

const payload = {
  command: "npm run helm:template",
  renderer,
  helmCommand: "helm template banking-lab infra/helm/banking-lab",
  helmAvailable: renderer === "helm",
  structuralValidation: errors.length === 0 ? "pass" : "failed",
  documentCount: documents.length,
  resources: documents.map((document) => ({
    kind: document.kind,
    name: document.name,
    namespace: document.namespace
  })),
  productionClaim: false,
  syntheticOnly: true,
  note: renderer === "helm"
    ? "Helm CLI rendered the chart."
    : "Helm CLI was not installed; Node fallback rendered this chart's limited template subset for structural validation only.",
  errors
};
await writeJsonPreservingTimestamp(evidencePath, payload);

if (errors.length > 0) {
  throw new Error(`Helm template validation failed:\n${errors.join("\n")}`);
}

console.log(`Helm template validation passed with ${renderer}: ${documents.length} rendered resources.`);

async function renderTemplatesWithNodeFallback(): Promise<string> {
  const files = (await readdir(templatesDir)).filter((file) => file.endsWith(".yaml")).sort();
  const renderedTemplates = [];
  for (const file of files) {
    const source = await readFile(path.join(templatesDir, file), "utf8");
    renderedTemplates.push(applySimpleTemplate(source));
  }
  return renderedTemplates.join("\n---\n");
}

function applySimpleTemplate(source: string): string {
  return source.replace(/\{\{\s*([^}]+?)\s*\}\}/g, (_match, expression: string) => {
    const normalized = expression.trim();
    const quote = normalized.endsWith("| quote");
    const key = quote ? normalized.replace(/\s*\|\s*quote$/, "").trim() : normalized;
    const raw = replacements[key];
    if (raw === undefined) {
      throw new Error(`Unsupported Helm template expression in fallback renderer: ${expression}`);
    }
    return quote ? JSON.stringify(raw) : raw;
  });
}

function buildReplacementMap(source: string): Record<string, string> {
  const topLevel = (key: string) => {
    const value = new RegExp(`^${key}:\\s*(.+)$`, "m").exec(source)?.[1]?.trim();
    if (!value) {
      throw new Error(`Missing values key: ${key}`);
    }
    return value.replace(/^["']|["']$/g, "");
  };

  return {
    ".Release.Name": releaseName,
    ".Values.namespace": topLevel("namespace"),
    ".Values.syntheticOnly": topLevel("syntheticOnly"),
    ".Values.coreBanking.replicas": scalarFromSection(source, "coreBanking", ["replicas"]),
    ".Values.coreBanking.image": scalarFromSection(source, "coreBanking", ["image"]),
    ".Values.coreBanking.port": scalarFromSection(source, "coreBanking", ["port"]),
    ".Values.coreBanking.resources.requests.cpu": scalarFromSection(source, "coreBanking", ["resources", "requests", "cpu"]),
    ".Values.coreBanking.resources.requests.memory": scalarFromSection(source, "coreBanking", ["resources", "requests", "memory"]),
    ".Values.coreBanking.resources.limits.cpu": scalarFromSection(source, "coreBanking", ["resources", "limits", "cpu"]),
    ".Values.coreBanking.resources.limits.memory": scalarFromSection(source, "coreBanking", ["resources", "limits", "memory"]),
    ".Values.reportingService.replicas": scalarFromSection(source, "reportingService", ["replicas"]),
    ".Values.reportingService.domainEventPublisherReplicas": scalarFromSection(source, "reportingService", ["domainEventPublisherReplicas"]),
    ".Values.reportingService.image": scalarFromSection(source, "reportingService", ["image"]),
    ".Values.reportingService.port": scalarFromSection(source, "reportingService", ["port"]),
    ".Values.reportingService.securityAudience": scalarFromSection(source, "reportingService", ["securityAudience"]),
    ".Values.reportingService.domainEventPublisherBootstrapServers": scalarFromSection(source, "reportingService", ["domainEventPublisherBootstrapServers"]),
    ".Values.reportingService.domainEventPublisherTopic": scalarFromSection(source, "reportingService", ["domainEventPublisherTopic"]),
    ".Values.reportingService.domainEventPublisherClientId": scalarFromSection(source, "reportingService", ["domainEventPublisherClientId"]),
    ".Values.reportingService.domainEventPublisherDeadLetterThreshold": scalarFromSection(source, "reportingService", ["domainEventPublisherDeadLetterThreshold"]),
    ".Values.reportingService.domainEventPublisherRetryDelaySeconds": scalarFromSection(source, "reportingService", ["domainEventPublisherRetryDelaySeconds"]),
    ".Values.reportingService.domainEventPublisherEventTypes": scalarFromSection(source, "reportingService", ["domainEventPublisherEventTypes"]),
    ".Values.reportingService.resources.requests.cpu": scalarFromSection(source, "reportingService", ["resources", "requests", "cpu"]),
    ".Values.reportingService.resources.requests.memory": scalarFromSection(source, "reportingService", ["resources", "requests", "memory"]),
    ".Values.reportingService.resources.limits.cpu": scalarFromSection(source, "reportingService", ["resources", "limits", "cpu"]),
    ".Values.reportingService.resources.limits.memory": scalarFromSection(source, "reportingService", ["resources", "limits", "memory"]),
    ".Values.reportingService.workerResources.requests.cpu": scalarFromSection(source, "reportingService", ["workerResources", "requests", "cpu"]),
    ".Values.reportingService.workerResources.requests.memory": scalarFromSection(source, "reportingService", ["workerResources", "requests", "memory"]),
    ".Values.reportingService.workerResources.limits.cpu": scalarFromSection(source, "reportingService", ["workerResources", "limits", "cpu"]),
    ".Values.reportingService.workerResources.limits.memory": scalarFromSection(source, "reportingService", ["workerResources", "limits", "memory"]),
    ".Values.paymentService.replicas": scalarFromSection(source, "paymentService", ["replicas"]),
    ".Values.paymentService.outboxWorkerReplicas": scalarFromSection(source, "paymentService", ["outboxWorkerReplicas"]),
    ".Values.paymentService.domainEventPublisherReplicas": scalarFromSection(source, "paymentService", ["domainEventPublisherReplicas"]),
    ".Values.paymentService.image": scalarFromSection(source, "paymentService", ["image"]),
    ".Values.paymentService.port": scalarFromSection(source, "paymentService", ["port"]),
    ".Values.paymentService.securityAudience": scalarFromSection(source, "paymentService", ["securityAudience"]),
    ".Values.paymentService.ledgerCommandTopic": scalarFromSection(source, "paymentService", ["ledgerCommandTopic"]),
    ".Values.paymentService.domainEventPublisherBootstrapServers": scalarFromSection(source, "paymentService", ["domainEventPublisherBootstrapServers"]),
    ".Values.paymentService.domainEventPublisherTopic": scalarFromSection(source, "paymentService", ["domainEventPublisherTopic"]),
    ".Values.paymentService.domainEventPublisherClientId": scalarFromSection(source, "paymentService", ["domainEventPublisherClientId"]),
    ".Values.paymentService.domainEventPublisherEventTypes": scalarFromSection(source, "paymentService", ["domainEventPublisherEventTypes"]),
    ".Values.paymentService.resources.requests.cpu": scalarFromSection(source, "paymentService", ["resources", "requests", "cpu"]),
    ".Values.paymentService.resources.requests.memory": scalarFromSection(source, "paymentService", ["resources", "requests", "memory"]),
    ".Values.paymentService.resources.limits.cpu": scalarFromSection(source, "paymentService", ["resources", "limits", "cpu"]),
    ".Values.paymentService.resources.limits.memory": scalarFromSection(source, "paymentService", ["resources", "limits", "memory"]),
    ".Values.paymentService.workerResources.requests.cpu": scalarFromSection(source, "paymentService", ["workerResources", "requests", "cpu"]),
    ".Values.paymentService.workerResources.requests.memory": scalarFromSection(source, "paymentService", ["workerResources", "requests", "memory"]),
    ".Values.paymentService.workerResources.limits.cpu": scalarFromSection(source, "paymentService", ["workerResources", "limits", "cpu"]),
    ".Values.paymentService.workerResources.limits.memory": scalarFromSection(source, "paymentService", ["workerResources", "limits", "memory"]),
    ".Values.notificationService.replicas": scalarFromSection(source, "notificationService", ["replicas"]),
    ".Values.notificationService.eventConsumerReplicas": scalarFromSection(source, "notificationService", ["eventConsumerReplicas"]),
    ".Values.notificationService.image": scalarFromSection(source, "notificationService", ["image"]),
    ".Values.notificationService.port": scalarFromSection(source, "notificationService", ["port"]),
    ".Values.notificationService.securityAudience": scalarFromSection(source, "notificationService", ["securityAudience"]),
    ".Values.notificationService.eventConsumerBootstrapServers": scalarFromSection(source, "notificationService", ["eventConsumerBootstrapServers"]),
    ".Values.notificationService.eventConsumerTopic": scalarFromSection(source, "notificationService", ["eventConsumerTopic"]),
    ".Values.notificationService.eventConsumerClientId": scalarFromSection(source, "notificationService", ["eventConsumerClientId"]),
    ".Values.notificationService.eventConsumerGroupId": scalarFromSection(source, "notificationService", ["eventConsumerGroupId"]),
    ".Values.notificationService.resources.requests.cpu": scalarFromSection(source, "notificationService", ["resources", "requests", "cpu"]),
    ".Values.notificationService.resources.requests.memory": scalarFromSection(source, "notificationService", ["resources", "requests", "memory"]),
    ".Values.notificationService.resources.limits.cpu": scalarFromSection(source, "notificationService", ["resources", "limits", "cpu"]),
    ".Values.notificationService.resources.limits.memory": scalarFromSection(source, "notificationService", ["resources", "limits", "memory"]),
    ".Values.notificationService.workerResources.requests.cpu": scalarFromSection(source, "notificationService", ["workerResources", "requests", "cpu"]),
    ".Values.notificationService.workerResources.requests.memory": scalarFromSection(source, "notificationService", ["workerResources", "requests", "memory"]),
    ".Values.notificationService.workerResources.limits.cpu": scalarFromSection(source, "notificationService", ["workerResources", "limits", "cpu"]),
    ".Values.notificationService.workerResources.limits.memory": scalarFromSection(source, "notificationService", ["workerResources", "limits", "memory"]),
    ".Values.temporalWorker.replicas": scalarFromSection(source, "temporalWorker", ["replicas"]),
    ".Values.temporalWorker.image": scalarFromSection(source, "temporalWorker", ["image"]),
    ".Values.temporalWorker.resources.requests.cpu": scalarFromSection(source, "temporalWorker", ["resources", "requests", "cpu"]),
    ".Values.temporalWorker.resources.requests.memory": scalarFromSection(source, "temporalWorker", ["resources", "requests", "memory"]),
    ".Values.temporalWorker.resources.limits.cpu": scalarFromSection(source, "temporalWorker", ["resources", "limits", "cpu"]),
    ".Values.temporalWorker.resources.limits.memory": scalarFromSection(source, "temporalWorker", ["resources", "limits", "memory"]),
    ".Values.keycloak.replicas": scalarFromSection(source, "keycloak", ["replicas"]),
    ".Values.keycloak.image": scalarFromSection(source, "keycloak", ["image"]),
    ".Values.keycloak.port": scalarFromSection(source, "keycloak", ["port"]),
    ".Values.keycloak.managementPort": scalarFromSection(source, "keycloak", ["managementPort"]),
    ".Values.redpanda.replicas": scalarFromSection(source, "redpanda", ["replicas"]),
    ".Values.redpanda.image": scalarFromSection(source, "redpanda", ["image"]),
    ".Values.redpanda.kafkaPort": scalarFromSection(source, "redpanda", ["kafkaPort"]),
    ".Values.redpanda.adminPort": scalarFromSection(source, "redpanda", ["adminPort"]),
    ".Values.redpanda.memory": scalarFromSection(source, "redpanda", ["memory"]),
    ".Values.temporalServer.replicas": scalarFromSection(source, "temporalServer", ["replicas"]),
    ".Values.temporalServer.image": scalarFromSection(source, "temporalServer", ["image"]),
    ".Values.temporalServer.port": scalarFromSection(source, "temporalServer", ["port"]),
    ".Values.ingress.className": scalarFromSection(source, "ingress", ["className"]),
    ".Values.ingress.tlsSecretName": scalarFromSection(source, "ingress", ["tlsSecretName"]),
    ".Values.ingress.appHost": scalarFromSection(source, "ingress", ["appHost"]),
    ".Values.ingress.authHost": scalarFromSection(source, "ingress", ["authHost"]),
    ".Values.ingress.reportingHost": scalarFromSection(source, "ingress", ["reportingHost"]),
    ".Values.ingress.paymentHost": scalarFromSection(source, "ingress", ["paymentHost"]),
    ".Values.ingress.notificationHost": scalarFromSection(source, "ingress", ["notificationHost"]),
    ".Values.postgres.image": scalarFromSection(source, "postgres", ["image"]),
    ".Values.postgres.database": scalarFromSection(source, "postgres", ["database"]),
    ".Values.postgres.username": scalarFromSection(source, "postgres", ["username"]),
    ".Values.postgres.storage": scalarFromSection(source, "postgres", ["storage"]),
    ".Values.temporal.target": scalarFromSection(source, "temporal", ["target"]),
    ".Values.outbox.bootstrapServers": scalarFromSection(source, "outbox", ["bootstrapServers"]),
    ".Values.outbox.topic": scalarFromSection(source, "outbox", ["topic"]),
    ".Values.security.enabled": scalarFromSection(source, "security", ["enabled"]),
    ".Values.security.simulatorTokensEnabled": scalarFromSection(source, "security", ["simulatorTokensEnabled"]),
    ".Values.security.devSimulatorToken": scalarFromSection(source, "security", ["devSimulatorToken"]),
    ".Values.security.jwksUri": scalarFromSection(source, "security", ["jwksUri"]),
    ".Values.security.issuer": scalarFromSection(source, "security", ["issuer"]),
    ".Values.security.audience": scalarFromSection(source, "security", ["audience"]),
    ".Values.security.trustedDeviceEnforcementEnabled": scalarFromSection(source, "security", ["trustedDeviceEnforcementEnabled"]),
    ".Values.security.stepUpEnforcementEnabled": scalarFromSection(source, "security", ["stepUpEnforcementEnabled"]),
    ".Values.security.sessionEnforcementEnabled": scalarFromSection(source, "security", ["sessionEnforcementEnabled"])
  };
}
