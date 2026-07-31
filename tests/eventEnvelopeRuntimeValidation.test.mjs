import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

const evidencePath = "docs/test-evidence/generated/event-envelope-runtime-validation.json";

test("runtime event envelope validation gate checks schemas headers and producer markers", async () => {
  const result = spawnSync("npm", ["run", "contracts:validate-runtime-events"], { encoding: "utf8" });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /Runtime event envelope validation passed/);

  const evidence = JSON.parse(await readFile(evidencePath, "utf8"));
  assert.equal(evidence.syntheticOnly, true);
  assert.equal(evidence.status, "pass");
  assert.equal(evidence.eventSchemaCount, 18);
  assert.equal(evidence.validatedEnvelopeCount, 18);
  assert.deepEqual(evidence.envelopeFields, [
    "eventId",
    "eventType",
    "aggregateType",
    "aggregateId",
    "occurredAt",
    "sourceService",
    "syntheticOnly",
    "schemaVersion",
    "payload"
  ]);

  for (const eventType of [
    "LedgerTransactionPosted",
    "PaymentInstructionLedgerPosted",
    "PaymentInstructionDeadLettered",
    "NotificationDeliveryFailed",
    "ReportRetentionSweepCompleted"
  ]) {
    assert.ok(evidence.eventTypes.includes(eventType), `missing event type evidence: ${eventType}`);
  }

  for (const id of [
    "core-banking-kafka-outbox-runtime-envelope",
    "payment-service-kafka-outbox-runtime-envelope",
    "reporting-service-kafka-outbox-runtime-envelope",
    "notification-service-runtime-envelope-consumer"
  ]) {
    const finding = evidence.sourceMarkerFindings.find((item) => item.id === id);
    assert.ok(finding, `missing source marker finding: ${id}`);
    assert.equal(finding.status, "pass");
  }
});
