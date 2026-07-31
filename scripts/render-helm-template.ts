import { spawnSync } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import {
  parseKubernetesDocuments,
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
  const rendered = source.replace(/\{\{\s*([^}]+?)\s*\}\}/g, (_match, expression: string) => {
    const [key, ...filters] = expression
      .trim()
      .split(/\s*\|\s*/u)
      .map((part) => part.trim())
      .filter(Boolean);
    const raw = replacements[key];
    if (raw === undefined) {
      throw new Error(`Unsupported Helm value in fallback renderer: ${key}`);
    }
    return filters.reduce((value, filter) => {
      if (filter === "quote") {
        return JSON.stringify(value);
      }
      throw new Error(`Unsupported Helm template filter in fallback renderer: ${filter}`);
    }, raw);
  });
  if (/\{\{|\}\}/u.test(rendered)) {
    throw new Error("Fallback Helm renderer left unresolved template delimiters.");
  }
  return rendered;
}

function buildReplacementMap(source: string): Record<string, string> {
  const values = parseSimpleYamlScalars(source);
  return {
    ".Release.Name": releaseName,
    ...Object.fromEntries(
      Object.entries(values).map(([key, value]) => [`.Values.${key}`, value])
    )
  };
}

function parseSimpleYamlScalars(source: string): Record<string, string> {
  const result: Record<string, string> = {};
  const parents: Array<{ readonly indent: number; readonly key: string }> = [];

  for (const [index, line] of source.split(/\r?\n/u).entries()) {
    if (!line.trim() || line.trimStart().startsWith("#")) {
      continue;
    }
    const match = /^(\s*)([A-Za-z0-9_-]+):(?:\s*(.*))?$/u.exec(line);
    if (!match) {
      throw new Error(`Unsupported values.yaml syntax at line ${index + 1}: ${line}`);
    }
    const indent = match[1].replaceAll("\t", "  ").length;
    const key = match[2];
    const rawValue = match[3]?.trim() ?? "";
    while (parents.length > 0 && parents[parents.length - 1].indent >= indent) {
      parents.pop();
    }
    if (!rawValue) {
      parents.push({ indent, key });
      continue;
    }
    const path = [...parents.map((parent) => parent.key), key].join(".");
    result[path] = normalizeYamlScalar(rawValue);
  }
  return result;
}

function normalizeYamlScalar(value: string): string {
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  const commentIndex = value.indexOf(" #");
  return (commentIndex >= 0 ? value.slice(0, commentIndex) : value).trim();
}
