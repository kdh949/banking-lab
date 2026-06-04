import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

test("Kubernetes and Helm validation scripts pass with generated evidence", async () => {
  const k8s = await execFileAsync("npm", ["run", "k8s:validate"], { maxBuffer: 1024 * 1024 });
  assert.equal(k8s.stderr, "");
  assert.match(k8s.stdout, /Kubernetes manifest validation passed/);

  const helm = await execFileAsync("npm", ["run", "helm:template"], { maxBuffer: 1024 * 1024 });
  assert.equal(helm.stderr, "");
  assert.match(helm.stdout, /Helm template validation passed/);

  const k8sEvidence = JSON.parse(await readFile("docs/test-evidence/generated/k8s-validation.json", "utf8"));
  assert.equal(k8sEvidence.structuralValidation, "pass");
  assert.equal(k8sEvidence.syntheticOnly, true);
  assert.ok(k8sEvidence.documentCount >= 11);

  const helmEvidence = JSON.parse(await readFile("docs/test-evidence/generated/helm-template-validation.json", "utf8"));
  assert.equal(helmEvidence.structuralValidation, "pass");
  assert.equal(helmEvidence.syntheticOnly, true);
  assert.ok(helmEvidence.documentCount >= 3);
});

test("platform deployment skeleton has required phase F artifacts", async () => {
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));
  assert.equal(packageJson.scripts["k8s:validate"], "node --experimental-strip-types scripts/validate-k8s-manifests.ts");
  assert.equal(packageJson.scripts["helm:template"], "node --experimental-strip-types scripts/render-helm-template.ts");

  const k8sValidation = await readFile("scripts/validate-k8s-manifests.ts", "utf8");
  const helmTemplate = await readFile("scripts/render-helm-template.ts", "utf8");
  const platformEvidence = await readFile("docs/test-evidence/platform-deployment-validation.md", "utf8");
  const secretExample = await readFile("infra/k8s/secret.example.yaml", "utf8");

  assert.match(k8sValidation, /secret\.example\.yaml/);
  assert.match(helmTemplate, /node-structural/);
  assert.match(platformEvidence, /not production deployment proof/i);
  assert.match(secretExample, /replace-with-local-synthetic-password/);
  assert.doesNotMatch(secretExample, /real customer|real money|real pii/i);
});
