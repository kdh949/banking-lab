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
  "Deployment/banking-lab-temporal-worker",
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
    ".Values.temporalWorker.replicas": scalarFromSection(source, "temporalWorker", ["replicas"]),
    ".Values.temporalWorker.image": scalarFromSection(source, "temporalWorker", ["image"]),
    ".Values.temporalWorker.resources.requests.cpu": scalarFromSection(source, "temporalWorker", ["resources", "requests", "cpu"]),
    ".Values.temporalWorker.resources.requests.memory": scalarFromSection(source, "temporalWorker", ["resources", "requests", "memory"]),
    ".Values.temporalWorker.resources.limits.cpu": scalarFromSection(source, "temporalWorker", ["resources", "limits", "cpu"]),
    ".Values.temporalWorker.resources.limits.memory": scalarFromSection(source, "temporalWorker", ["resources", "limits", "memory"]),
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
