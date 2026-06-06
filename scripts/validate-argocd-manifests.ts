import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import {
  parseKubernetesDocuments,
  writeJsonPreservingTimestamp
} from "./k8s-yaml-utils.ts";

const argocdDir = path.join("infra", "argocd");
const evidencePath = path.join("docs", "test-evidence", "generated", "argocd-validation.json");
const requiredFiles = [
  "banking-lab-application.yaml",
  "banking-lab-app.yaml"
];

const files = (await readdir(argocdDir)).filter((file) => file.endsWith(".yaml")).sort();
const errors: string[] = [];
for (const file of requiredFiles) {
  if (!files.includes(file)) {
    errors.push(`Missing required Argo CD manifest: infra/argocd/${file}`);
  }
}

const documents = [];
for (const file of files) {
  const filePath = path.join(argocdDir, file);
  documents.push(...parseKubernetesDocuments(await readFile(filePath, "utf8"), filePath));
}

const applications = documents.filter((document) => document.apiVersion === "argoproj.io/v1alpha1" && document.kind === "Application");
if (applications.length !== files.length) {
  errors.push("Every infra/argocd YAML document must be an argoproj.io/v1alpha1 Application.");
}

for (const application of applications) {
  if (application.name !== "banking-lab") {
    errors.push(`${application.file} must define Application/banking-lab.`);
  }
  if (application.namespace !== "argocd") {
    errors.push(`${application.file} must live in the argocd namespace.`);
  }
  requireText(application.content, "repoURL: https://example.invalid/synthetic/banking-lab.git", `${application.file} must use the synthetic example.invalid repository URL.`);
  requireText(application.content, "path: infra/helm/banking-lab", `${application.file} must point Argo CD at the Helm chart path.`);
  requireText(application.content, "server: https://kubernetes.default.svc", `${application.file} must target the in-cluster Kubernetes API.`);
  requireText(application.content, "namespace: banking-lab", `${application.file} must deploy into the banking-lab namespace.`);
  requireText(application.content, "prune: false", `${application.file} must keep automatic prune disabled for the lab manifest.`);
  requireText(application.content, "selfHeal: true", `${application.file} must enable selfHeal for drift visibility.`);
  requireText(application.content, "CreateNamespace=true", `${application.file} must let Argo CD create the synthetic namespace.`);
  if (/password|secret|token|private[_ -]?key/i.test(application.content)) {
    errors.push(`${application.file} must not embed credentials or secret material.`);
  }
  if (/real[_ -]?(customer|money|pii|bank|network)/i.test(application.content)) {
    errors.push(`${application.file} must not reference real customer, money, PII, bank, or network data.`);
  }
}

const payload = {
  command: "npm run argocd:validate",
  structuralValidation: errors.length === 0 ? "pass" : "failed",
  requiredFiles,
  applicationCount: applications.length,
  applications: applications.map((application) => ({
    file: application.file,
    name: application.name,
    namespace: application.namespace
  })),
  productionClaim: false,
  syntheticOnly: true,
  errors
};
await writeJsonPreservingTimestamp(evidencePath, payload);

if (errors.length > 0) {
  throw new Error(`Argo CD validation failed:\n${errors.join("\n")}`);
}

console.log(`Argo CD manifest validation passed: ${applications.length} applications.`);

function requireText(content: string, needle: string, message: string): void {
  if (!content.includes(needle)) {
    errors.push(message);
  }
}
