import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("payment instruction state distinguishes internal ledger posting from external settlement", async () => {
  const models = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentModels.kt", "utf8");
  const service = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/domain/PaymentInstructionService.kt", "utf8");
  const repository = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/persistence/PaymentRepository.kt", "utf8");
  const controller = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/api/PaymentController.kt", "utf8");
  const migration = await readFile("services/payment-service/src/main/resources/db/migration/V006__payment_ledger_posted_semantics.sql", "utf8");

  assert.match(models, /POSTING_REQUESTED,\s+LEDGER_POSTED,/u);
  assert.doesNotMatch(models, /PaymentInstructionStatus[\s\S]*?\bSETTLED\b/u);
  assert.match(service, /fun recordLedgerPosting/u);
  assert.match(service, /PaymentInstructionStatus\.LEDGER_POSTED/u);
  assert.match(service, /eventType = "PaymentInstructionLedgerPosted"/u);
  assert.match(service, /"externalSettlementCompleted" to false/u);
  assert.doesNotMatch(service, /eventType = "PaymentInstructionSettled"/u);
  assert.match(repository, /SET status = 'LEDGER_POSTED'/u);
  assert.match(controller, /\/instructions\/\{instructionId\}\/ledger-postings/u);
  assert.match(controller, /@Deprecated\("Use \/api\/payments\/instructions/u);
  assert.match(controller, /\/instructions\/\{instructionId\}\/settlements/u);
  assert.match(migration, /WHERE status = 'SETTLED'/u);
  assert.match(migration, /event_type = 'PaymentInstructionLedgerPosted'/u);
  assert.match(migration, /status IN \('POSTING_REQUESTED', 'LEDGER_POSTED', 'CANCELED', 'FAILED'\)/u);
});

test("canonical ledger-posted contracts coexist with explicit legacy compatibility contracts", async () => {
  const openapi = await readFile("contracts/openapi/payment-service.yaml", "utf8");
  const asyncapi = await readFile("contracts/asyncapi/banking-lab-events.yaml", "utf8");
  const canonical = JSON.parse(await readFile("contracts/events/payment-instruction-ledger-posted.schema.json", "utf8"));
  const legacy = JSON.parse(await readFile("contracts/events/payment-instruction-settled.schema.json", "utf8"));
  const publisher = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/eventing/PaymentKafkaModels.kt", "utf8");
  const application = await readFile("services/payment-service/src/main/resources/application.yml", "utf8");

  assert.match(openapi, /\/api\/payments\/instructions\/\{instructionId\}\/ledger-postings:/u);
  assert.match(openapi, /\/api\/payments\/instructions\/\{instructionId\}\/settlements:[\s\S]*?deprecated: true/u);
  assert.match(openapi, /enum: \[POSTING_REQUESTED, LEDGER_POSTED, CANCELED, FAILED\]/u);
  assert.match(asyncapi, /payment\.instruction\.ledger-posted/u);
  assert.match(asyncapi, /payment\.instruction\.settled/u);
  assert.equal(canonical.title, "PaymentInstructionLedgerPosted");
  assert.equal(canonical.properties.status.const, "LEDGER_POSTED");
  assert.equal(canonical.properties.externalSettlementCompleted.const, false);
  assert.equal(legacy.title, "PaymentInstructionSettled");
  assert.equal(legacy.properties.status.const, "SETTLED");
  assert.match(publisher, /PaymentInstructionLedgerPosted/u);
  assert.doesNotMatch(publisher, /PaymentInstructionSettled/u);
  assert.match(application, /PaymentInstructionLedgerPosted/u);
  assert.doesNotMatch(application, /PaymentInstructionSettled/u);
});
