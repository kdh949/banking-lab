
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const migrationPath = "services/payment-service/src/main/resources/db/migration/V008__payment_three_way_reconciliation.sql";
const servicePath = "services/payment-service/src/main/kotlin/lab/banking/payment/reconciliation/PaymentReconciliationService.kt";
const coreEvidencePath = "services/core-banking/src/main/kotlin/lab/banking/core/ledger/evidence/PaymentLedgerEvidenceService.kt";

test("three-way payment reconciliation persists mismatch taxonomy owner SLA and future approval boundary", async () => {
  const migration = await readFile(migrationPath, "utf8");
  for (const table of [
    "payment_reconciliation_runs",
    "payment_reconciliation_results",
    "payment_reconciliation_access_audit_events"
  ]) {
    assert.match(migration, new RegExp(`CREATE TABLE ${table}`));
  }
  for (const mismatch of [
    "MISSING_PAYMENT",
    "MISSING_LEDGER",
    "MISSING_EXTERNAL",
    "AMOUNT_MISMATCH",
    "STATUS_MISMATCH",
    "DUPLICATE_EXTERNAL",
    "VALUE_DATE_MISMATCH",
    "LATE_SETTLEMENT"
  ]) {
    assert.match(migration, new RegExp(mismatch));
  }
  for (const field of ["owner_id", "detected_at", "due_at", "resolution", "approval_id"]) {
    assert.match(migration, new RegExp(field));
  }
  assert.match(migration, /one authoritative three-way reconciliation|Three-way comparison/i);
});

test("payment service obtains core ledger evidence through a service boundary outside its local transaction", async () => {
  const service = await readFile(servicePath, "utf8");
  const coreEvidence = await readFile(coreEvidencePath, "utf8");
  assert.match(service, /CoreLedgerEvidenceClient/);
  assert.match(service, /coreLedgerEvidenceClient\.paymentPostings/);
  assert.match(service, /TransactionTemplate/);
  assert.doesNotMatch(service, /FROM ledger_transactions/);
  assert.match(coreEvidence, /FROM ledger_transactions lt/);
  assert.match(coreEvidence, /JOIN ledger_postings lp/);
  assert.match(coreEvidence, /PAYMENT_LEDGER_EVIDENCE_VIEW/);
  assert.match(coreEvidence, /total_debit_minor/);
  assert.match(coreEvidence, /total_credit_minor/);
});

test("reconciliation API and role policy expose ops commands and reason-audited control reads", async () => {
  const paymentContract = await readFile("contracts/openapi/payment-service.yaml", "utf8");
  const coreContract = await readFile("contracts/openapi/core-banking.yaml", "utf8");
  const paymentAuth = await readFile("services/payment-service/src/main/kotlin/lab/banking/payment/security/PaymentAuthorizationFilter.kt", "utf8");
  const coreAuth = await readFile("services/core-banking/src/main/kotlin/lab/banking/core/security/BankingLabRouteAuthorizationManager.kt", "utf8");
  for (const path of [
    "/api/payments/reconciliation/runs",
    "/api/payments/reconciliation/runs/{runId}",
    "/api/payments/reconciliation/exceptions"
  ]) {
    assert.match(paymentContract, new RegExp(path.replaceAll("/", "\\/")));
  }
  assert.match(coreContract, /\/api\/ledger\/payment-postings\/evidence/);
  assert.match(paymentContract, /PAYMENT_RECONCILIATION_REASON_REQUIRED|reason:/);
  assert.match(paymentAuth, /OPS_OPERATOR/);
  assert.match(paymentAuth, /AUDITOR/);
  assert.match(coreAuth, /PAYMENT_SERVICE/);
  assert.match(coreAuth, /payment-postings\/evidence/);
});
