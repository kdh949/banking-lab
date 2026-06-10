import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

test("Phase 4 contract validation scripts are wired and pass", () => {
  for (const script of ["contracts:lint", "contracts:check-client", "contracts:check-events", "contracts:diff-openapi"]) {
    const result = spawnSync("npm", ["run", script], { encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr || result.stdout);
  }
});

test("core and reporting contracts expose shared-client and structured-error gates", async () => {
  const core = await readFile("contracts/openapi/core-banking.yaml", "utf8");
  const reporting = await readFile("contracts/openapi/reporting-service.yaml", "utf8");
  const asyncapi = await readFile("contracts/asyncapi/banking-lab-events.yaml", "utf8");

  assert.match(core, /operationId: requestCustomerTransfer/);
  assert.match(core, /operationId: startLedgerProjectionDriftRun/);
  assert.match(core, /operationId: authSession/);
  assert.match(core, /operationId: syntheticAmlModelCard/);
  assert.match(core, /StructuredErrorResponse/);
  assert.match(reporting, /operationId: generateReportArtifact/);
  assert.match(reporting, /x-idempotency-policy: body\.idempotencyKey/);
  assert.match(asyncapi, /report\.artifact\.generated/);
  assert.match(asyncapi, /sourceService/);
  assert.match(asyncapi, /occurredAt/);
});
