import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import test from "node:test";

const generatedFiles = [
  ["core-banking", "docs/test-evidence/generated/openapi/core-banking.generated.json", 140],
  ["payment-service", "docs/test-evidence/generated/openapi/payment-service.generated.json", 14],
  ["notification-service", "docs/test-evidence/generated/openapi/notification-service.generated.json", 16],
  ["reporting-service", "docs/test-evidence/generated/openapi/reporting-service.generated.json", 6]
];

test("OpenAPI generated source diff gate compares Kotlin controller routes to checked-in contracts", async () => {
  const result = spawnSync("npm", ["run", "contracts:diff-openapi"], { encoding: "utf8" });

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /OpenAPI generated source diff passed/);

  for (const [serviceId, filePath, minimumOperationCount] of generatedFiles) {
    const snapshot = JSON.parse(await readFile(filePath, "utf8"));
    assert.equal(snapshot.generator, "kotlin-controller-source");
    assert.equal(snapshot.serviceId, serviceId);
    assert.equal(snapshot.syntheticOnly, true);
    assert.ok(snapshot.operations.length >= minimumOperationCount, `${serviceId} operation count drifted too low`);
  }

  const core = JSON.parse(await readFile("docs/test-evidence/generated/openapi/core-banking.generated.json", "utf8"));
  const coreKeys = new Set(core.operations.map((operation) => `${operation.method} ${operation.path}`));
  assert.ok(coreKeys.has("GET /api/auth/session"));
  assert.ok(coreKeys.has("POST /api/ops/security/break-glass"));
  assert.ok(coreKeys.has("GET /api/parity/structured-errors/{code}"));
  const coreSchemaNames = new Set(core.dtoSchemas.map((schema) => schema.name));
  assert.ok(coreSchemaNames.has("DepositCommand"));
  assert.ok(coreSchemaNames.has("InternalTransferCommand"));
  assert.ok(coreSchemaNames.has("LedgerCommandResult"));
  assert.ok(coreSchemaNames.has("PostingDirection"));
  assert.ok(coreSchemaNames.has("ReconciliationClosingResponse"));
  assert.ok(coreSchemaNames.has("ReconciliationItemDto"));
  assert.ok(coreSchemaNames.has("TemporalWorkflowReference"));

  const coreContract = await readFile("contracts/openapi/core-banking.yaml", "utf8");
  assert.match(coreContract, /postLedgerDeposit[\s\S]*#\/components\/schemas\/DepositCommand/);
  assert.match(coreContract, /postLedgerTransfer[\s\S]*#\/components\/schemas\/InternalTransferCommand/);
  assert.match(coreContract, /postLedgerAdjustment[\s\S]*#\/components\/schemas\/AdjustmentCommand/);
  assert.match(coreContract, /closeBusinessDay[\s\S]*#\/components\/schemas\/ReconciliationClosingResponse/);

  const payment = JSON.parse(await readFile("docs/test-evidence/generated/openapi/payment-service.generated.json", "utf8"));
  const paymentSchemaNames = new Set(payment.dtoSchemas.map((schema) => schema.name));
  assert.ok(paymentSchemaNames.has("CreatePaymentInstructionRequest"));
  assert.ok(paymentSchemaNames.has("PaymentInstructionDto"));
  assert.ok(paymentSchemaNames.has("PaymentAutopayAgreementDto"));
  assert.ok(paymentSchemaNames.has("PaymentCancellationRequestStatus"));

  const paymentContract = await readFile("contracts/openapi/payment-service.yaml", "utf8");
  assert.doesNotMatch(paymentContract, /AnyJsonResponse/);
  assert.match(paymentContract, /PaymentInstruction:/);
  assert.match(paymentContract, /enum: \[PENDING, APPROVED, REJECTED\]/);

  const notification = JSON.parse(await readFile("docs/test-evidence/generated/openapi/notification-service.generated.json", "utf8"));
  const notificationSchemaNames = new Set(notification.dtoSchemas.map((schema) => schema.name));
  assert.ok(notificationSchemaNames.has("ConsumeNotificationEventRequest"));
  assert.ok(notificationSchemaNames.has("NotificationDeliveryDto"));
  assert.ok(notificationSchemaNames.has("NotificationTemplateChangeRequestDto"));
  assert.ok(notificationSchemaNames.has("NotificationWorkflowStatus"));
  assert.ok(notificationSchemaNames.has("NotificationWorkflowTimelineEntryDto"));

  const notificationContract = await readFile("contracts/openapi/notification-service.yaml", "utf8");
  assert.doesNotMatch(notificationContract, /AnyJsonResponse/);
  assert.match(notificationContract, /NotificationTemplateChangeRequest:/);
  assert.match(notificationContract, /enum: \[PENDING_REVIEW, APPROVED, REJECTED\]/);

  const reporting = JSON.parse(await readFile("docs/test-evidence/generated/openapi/reporting-service.generated.json", "utf8"));
  const reportingSchemaNames = new Set(reporting.dtoSchemas.map((schema) => schema.name));
  assert.ok(reportingSchemaNames.has("GenerateReportCommand"));
  assert.ok(reportingSchemaNames.has("ReportArtifactDto"));
  assert.ok(reportingSchemaNames.has("ReportRetentionSweepResponse"));
  assert.ok(reportingSchemaNames.has("ReportingWorkflowTimelineEntryDto"));

  const reportingContract = await readFile("contracts/openapi/reporting-service.yaml", "utf8");
  assert.doesNotMatch(reportingContract, /AnyJsonResponse/);
  assert.match(reportingContract, /ReportArtifactDto:/);
});
