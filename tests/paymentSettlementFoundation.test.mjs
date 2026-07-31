import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("payment settlement foundation preserves independent external file provenance", async () => {
  const migration = await readFile(
    "services/payment-service/src/main/resources/db/migration/V007__payment_external_settlement_batch_foundation.sql",
    "utf8"
  );
  const parser = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/settlement/ExternalSettlementCsvParser.kt",
    "utf8"
  );
  const repository = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/settlement/PaymentSettlementRepository.kt",
    "utf8"
  );
  const service = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/settlement/PaymentSettlementService.kt",
    "utf8"
  );
  const fixture = await readFile(
    "services/payment-service/src/integrationTest/resources/external-settlement/accepted-clearing.csv",
    "utf8"
  );

  for (const table of [
    "payment_external_settlement_imports",
    "payment_external_settlement_lines",
    "payment_settlement_batch_runs",
    "payment_settlement_batches",
    "payment_settlement_batch_items"
  ]) {
    assert.match(migration, new RegExp(`CREATE TABLE ${table}`));
  }

  assert.match(migration, /file_sha256 TEXT NOT NULL UNIQUE/);
  assert.match(migration, /raw_line TEXT NOT NULL/);
  assert.match(migration, /payment_settlement_batch_arithmetic/);
  assert.match(migration, /INCLUDED_IN_BATCH does not mean payout or external settlement finality/);
  assert.match(parser, /expectedHeader/);
  assert.match(parser, /ACCEPTED, REJECTED, or RETURNED/);
  assert.match(service, /MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(service, /PAYMENT_EXTERNAL_SETTLEMENT_FILE_DUPLICATE/);
  assert.match(service, /applyBasisPoints\(grossAmountMinor, request\.feeRateBps\)/);
  assert.match(service, /applyBasisPoints\(feeAmountMinor, request\.vatRateBps\)/);
  assert.match(service, /SettlementBatchStatus\.INCLUDED_IN_BATCH/);
  assert.doesNotMatch(repository, /FROM payment_instructions/);
  assert.doesNotMatch(repository, /FROM ledger_transactions/);
  assert.match(fixture, /EXT-UTIL-001/);
  assert.match(fixture, /EXT-TAX-REJECTED/);
});

test("payment settlement API and authorization policy expose ops import and batch routes", async () => {
  const controller = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/settlement/PaymentSettlementController.kt",
    "utf8"
  );
  const authorization = await readFile(
    "services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentAuthorizationFilter.kt",
    "utf8"
  );
  const openApi = await readFile("contracts/openapi/payment-service.yaml", "utf8");
  const integration = await readFile(
    "services/payment-service/src/integrationTest/kotlin/lab/banking/payment/PaymentSettlementIntegrationTest.kt",
    "utf8"
  );

  for (const route of [
    "/imports",
    "/imports/{importId}",
    "/batch-runs",
    "/batch-runs/{batchRunId}"
  ]) {
    assert.match(controller, new RegExp(route.replaceAll("/", "\\/").replaceAll("{", "\\{").replaceAll("}", "\\}")));
  }

  assert.match(authorization, /paymentSettlementImport/);
  assert.match(authorization, /paymentSettlementBatchRun/);
  assert.match(authorization, /OPS_OPERATOR/);
  assert.match(authorization, /AUDITOR/);
  assert.match(openApi, /\/api\/payments\/settlement\/imports/);
  assert.match(openApi, /\/api\/payments\/settlement\/batch-runs/);
  assert.match(openApi, /ImportExternalSettlementCsvRequest/);
  assert.match(openApi, /CreateSettlementBatchRunRequest/);
  assert.match(integration, /PAYMENT_EXTERNAL_SETTLEMENT_FILE_DUPLICATE/);
  assert.match(integration, /grossAmountMinor = 150_000/);
  assert.match(integration, /netAmountMinor = 148_350/);
});
