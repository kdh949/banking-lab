import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

const evidencePath = "docs/test-evidence/generated/contract-runtime-evidence.json";
const docPath = "docs/test-evidence/contract-runtime-evidence-boundary.md";

test("contract runtime evidence boundary records structural passes and missing runtime gates", async () => {
  const result = spawnSync("npm", ["run", "contracts:runtime-evidence"], { encoding: "utf8" });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Contract runtime evidence boundary passed/);

  const evidence = JSON.parse(await readFile(evidencePath, "utf8"));
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.status, "partial");
  assert.ok(evidence.openApi.operationCount > 100);
  assert.ok(evidence.openApi.clientMethodCount > 100);
  assert.deepEqual(evidence.openApi.structuralGateScripts, [
    "contracts:lint",
    "contracts:check-client",
    "contracts:diff-openapi"
  ]);
  assert.equal(evidence.openApi.generatedDtoDiffGate.status, "partial");
  assert.equal(evidence.openApi.generatedDtoDiffGate.expectedScript, "contracts:diff-openapi");
  assert.match(evidence.openApi.generatedDtoDiffGate.note, /reporting-service DTO schemas are generated from Kotlin data classes/);

  assert.ok(evidence.asyncApi.eventSchemaCount >= 17);
  assert.deepEqual(evidence.asyncApi.envelopeFields, [
    "syntheticOnly",
    "sourceService",
    "eventType",
    "aggregateId",
    "occurredAt"
  ]);
  assert.equal(evidence.asyncApi.runtimeEventValidationGate.status, "partial");
  assert.equal(evidence.asyncApi.runtimeEventValidationGate.expectedScript, "contracts:validate-runtime-events");
  assert.match(evidence.asyncApi.runtimeEventValidationGate.note, /validates synthetic runtime envelope fixtures/);

  for (const id of [
    "payment-service-kafka-producer-envelope-source",
    "reporting-service-kafka-producer-envelope-source",
    "notification-service-consumer-envelope-source",
    "core-banking-outbox-source"
  ]) {
    const finding = evidence.sourceInspection.find((item) => item.id === id);
    assert.ok(finding, `missing source inspection finding: ${id}`);
    assert.equal(finding.status, "pass");
  }
});

test("contract runtime evidence docs avoid DTO and runtime event overclaims", async () => {
  const doc = await readFile(docPath, "utf8");
  const packageJson = JSON.parse(await readFile("package.json", "utf8"));

  assert.match(doc, /Status: partial/);
  assert.match(doc, /Kotlin controller source-to-OpenAPI path\/method diffing is wired through `contracts:diff-openapi`/);
  assert.match(doc, /Do not claim reporting-service DTO schema parity is equivalent to full core-banking Spring\/Jackson\/springdoc DTO-level OpenAPI schema parity/);
  assert.match(doc, /Do not claim this fixture\/schema\/source-marker gate is equivalent to exhaustive live broker producer\/consumer coverage/);
  assert.match(doc, /synthetic-only/);
  assert.ok(packageJson.scripts["contracts:runtime-evidence"]);
  assert.ok(packageJson.scripts["contracts:diff-openapi"]);
  assert.ok(packageJson.scripts["contracts:validate-runtime-events"]);
});
