import { expect, test } from "@playwright/test";

const paymentApiBaseUrl = process.env.BANKING_LAB_E2E_PAYMENT_API_BASE_URL ?? "";
const importId = "ESI-E2E-PORTFOLIO-001";
const runId = "PRR-E2E-PORTFOLIO-001";
const requests: Array<{ readonly path: string; readonly method: string; readonly body: unknown }> = [];

const importResponse = {
  replayed: false,
  item: {
    externalSettlementImportId: importId,
    originalFileName: "portfolio-e2e.csv",
    institutionCode: "SYN-CLEARING-HOUSE",
    fileSha256: "b69da4f40e09b5906013668c7582399f2f6cb3577e8077a083a941f448a9e5cb",
    fileByteSize: 612,
    businessDate: "2026-02-03",
    status: "IMPORTED",
    lineCount: 4,
    acceptedLineCount: 3,
    rejectedLineCount: 1,
    requestedBy: "ops01",
    reason: "Portfolio settlement provenance walkthrough",
    syntheticOnly: true,
    receivedAt: "2026-02-03T09:03:00+09:00",
    lines: []
  }
};

const batchResponse = {
  settlementBatchRunId: "SBR-E2E-PORTFOLIO-001",
  externalSettlementImportId: importId,
  replayed: false,
  syntheticOnly: true,
  items: [
    {
      settlementBatchId: "STB-E2E-UTILITY",
      settlementBatchRunId: "SBR-E2E-PORTFOLIO-001",
      externalSettlementImportId: importId,
      billerId: "SYN-BILLER-UTILITY",
      currency: "KRW",
      businessDate: "2026-02-03",
      valueDate: "2026-02-04",
      cutoffAt: "2026-02-03T15:30:00+09:00",
      grossAmountMinor: 150000,
      feeRateBps: 100,
      feeAmountMinor: 1500,
      vatRateBps: 1000,
      vatAmountMinor: 150,
      adjustmentAmountMinor: 0,
      netAmountMinor: 148350,
      itemCount: 2,
      status: "INCLUDED_IN_BATCH",
      externalPayoutReference: null,
      syntheticOnly: true,
      createdAt: "2026-02-03T15:31:00+09:00"
    },
    {
      settlementBatchId: "STB-E2E-TELCO",
      settlementBatchRunId: "SBR-E2E-PORTFOLIO-001",
      externalSettlementImportId: importId,
      billerId: "SYN-BILLER-TELCO",
      currency: "KRW",
      businessDate: "2026-02-03",
      valueDate: "2026-02-05",
      cutoffAt: "2026-02-03T15:30:00+09:00",
      grossAmountMinor: 20000,
      feeRateBps: 100,
      feeAmountMinor: 200,
      vatRateBps: 1000,
      vatAmountMinor: 20,
      adjustmentAmountMinor: 0,
      netAmountMinor: 19780,
      itemCount: 1,
      status: "INCLUDED_IN_BATCH",
      externalPayoutReference: null,
      syntheticOnly: true,
      createdAt: "2026-02-03T15:31:00+09:00"
    }
  ]
};

const reconciliationResults = [
  {
    paymentReconciliationResultId: "PRC-E2E-001",
    paymentReconciliationRunId: runId,
    externalSettlementLineId: "ESL-E2E-001",
    paymentInstructionId: "PAY-SYN-SETTLE-001",
    ledgerTransactionId: "TX-SYN-SETTLE-001",
    mismatchType: "MATCHED",
    resultStatus: "MATCHED",
    internalAmountMinor: 100000,
    ledgerAmountMinor: 100000,
    externalAmountMinor: 100000,
    currency: "KRW",
    externalStatus: "ACCEPTED",
    businessDate: "2026-02-03",
    expectedValueDate: "2026-02-04",
    actualValueDate: "2026-02-04",
    ownerId: "ops01",
    detectedReason: "All three independent sources agree",
    detectedAt: "2026-02-03T16:00:00+09:00",
    dueAt: "2026-02-05T16:00:00+09:00",
    agingDays: 1,
    overdue: false,
    resolution: null,
    approvalId: null,
    resolvedAt: null,
    syntheticOnly: true
  },
  {
    paymentReconciliationResultId: "PRC-E2E-002",
    paymentReconciliationRunId: runId,
    externalSettlementLineId: "ESL-E2E-002",
    paymentInstructionId: "PAY-SYN-SETTLE-002",
    ledgerTransactionId: "TX-SYN-SETTLE-002",
    mismatchType: "AMOUNT_MISMATCH",
    resultStatus: "OPEN",
    internalAmountMinor: 50000,
    ledgerAmountMinor: 50000,
    externalAmountMinor: 49000,
    currency: "KRW",
    externalStatus: "ACCEPTED",
    businessDate: "2026-02-03",
    expectedValueDate: "2026-02-04",
    actualValueDate: "2026-02-04",
    ownerId: "ops01",
    detectedReason: "External amount differs from payment and balanced ledger evidence",
    detectedAt: "2026-02-03T16:00:00+09:00",
    dueAt: "2026-02-05T16:00:00+09:00",
    agingDays: 1,
    overdue: false,
    resolution: null,
    approvalId: null,
    resolvedAt: null,
    syntheticOnly: true
  },
  {
    paymentReconciliationResultId: "PRC-E2E-003",
    paymentReconciliationRunId: runId,
    externalSettlementLineId: "ESL-E2E-003",
    paymentInstructionId: "PAY-SYN-SETTLE-003",
    ledgerTransactionId: null,
    mismatchType: "MISSING_LEDGER",
    resultStatus: "OPEN",
    internalAmountMinor: 20000,
    ledgerAmountMinor: null,
    externalAmountMinor: 20000,
    currency: "KRW",
    externalStatus: "ACCEPTED",
    businessDate: "2026-02-03",
    expectedValueDate: "2026-02-04",
    actualValueDate: "2026-02-05",
    ownerId: "ops01",
    detectedReason: "Payment and external line exist but balanced ledger evidence is missing",
    detectedAt: "2026-02-03T16:00:00+09:00",
    dueAt: "2026-02-05T16:00:00+09:00",
    agingDays: 3,
    overdue: true,
    resolution: null,
    approvalId: null,
    resolvedAt: null,
    syntheticOnly: true
  }
];

const reconciliationResponse = {
  item: {
    paymentReconciliationRunId: runId,
    externalSettlementImportId: importId,
    businessDate: "2026-02-03",
    expectedValueDate: "2026-02-04",
    allowedValueDateLagDays: 0,
    slaDays: 2,
    ownerId: "ops01",
    status: "EXCEPTIONS_OPEN",
    externalLineCount: 4,
    ledgerEvidenceCount: 3,
    matchedCount: 1,
    exceptionCount: 2,
    attemptCount: 1,
    failureMessage: null,
    syntheticOnly: true,
    createdAt: "2026-02-03T16:00:00+09:00",
    updatedAt: "2026-02-03T16:00:02+09:00",
    completedAt: "2026-02-03T16:00:02+09:00"
  },
  results: reconciliationResults,
  replayed: false,
  auditEventId: "PRA-E2E-RUN-001",
  syntheticOnly: true
};

test.beforeEach(async ({ page }) => {
  requests.length = 0;
  if (!paymentApiBaseUrl) return;
  await page.route(`${paymentApiBaseUrl}/api/payments/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const body = request.postDataJSON?.() ?? null;
    requests.push({ path: `${url.pathname}${url.search}`, method: request.method(), body });

    if (url.pathname === "/api/payments/settlement/imports" && request.method() === "POST") {
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(importResponse) });
      return;
    }
    if (url.pathname === "/api/payments/settlement/batch-runs" && request.method() === "POST") {
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(batchResponse) });
      return;
    }
    if (url.pathname === "/api/payments/reconciliation/runs" && request.method() === "POST") {
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify(reconciliationResponse) });
      return;
    }
    if (url.pathname === "/api/payments/reconciliation/exceptions" && request.method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: reconciliationResults.filter((item) => item.resultStatus === "OPEN"),
          auditEventId: "PRA-E2E-QUEUE-001",
          syntheticOnly: true
        })
      });
      return;
    }
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ message: "unmocked route" }) });
  });
});

test("ops settlement workbench explains the four-stage control flow", async ({ page }) => {
  await page.goto("http://127.0.0.1:3004");
  const workbench = page.getByTestId("payment-settlement-workbench");
  await expect(workbench).toBeVisible();
  await expect(page.getByRole("heading", { name: "Payment settlement and ledger reconciliation" })).toBeVisible();
  await expect(workbench).toContainText("Independent CSV → gross / fee / VAT / net batch");
  await expect(workbench).toContainText(paymentApiBaseUrl ? "Spring API connected" : "Guided synthetic fixture");
});

test("ops settlement workbench completes CSV to exception queue and uses payment-service contracts", async ({ page }) => {
  await page.goto("http://127.0.0.1:3004");
  await expect(page.getByTestId("payment-settlement-workbench")).toHaveAttribute("data-hydrated", "true");

  await page.getByRole("button", { name: "Import independent CSV" }).click();
  await expect(page.getByTestId("settlement-import-stage")).toContainText(/ESI-(E2E|PORTFOLIO)-/);
  await expect(page.getByTestId("settlement-import-stage")).toContainText("SHA-256");

  await page.getByRole("button", { name: "Build gross / fee / VAT / net batch" }).click();
  const batchStage = page.getByTestId("settlement-batch-stage");
  await expect(batchStage).toContainText("UTILITY");
  await expect(batchStage).toContainText("148,350");
  await expect(batchStage).toContainText("19,780");

  await page.getByRole("button", { name: "Run payment · ledger · external 3-way" }).click();
  const reconciliationStage = page.getByTestId("settlement-reconciliation-stage");
  await expect(reconciliationStage).toContainText(/PRR-(E2E|PORTFOLIO)-/);
  await expect(reconciliationStage).toContainText("MATCHED");
  await expect(reconciliationStage).toContainText("AMOUNT_MISMATCH");
  await expect(reconciliationStage).toContainText("MISSING_LEDGER");

  await page.getByRole("button", { name: "Refresh exception queue" }).click();
  const exceptionStage = page.getByTestId("settlement-exception-stage");
  await expect(exceptionStage).toContainText("Owner");
  await expect(exceptionStage).toContainText("Aging");
  await expect(exceptionStage).toContainText("AMOUNT_MISMATCH");
  await expect(exceptionStage).toContainText("MISSING_LEDGER");

  if (paymentApiBaseUrl) {
    expect(requests.map((request) => `${request.method} ${request.path}`)).toEqual([
      "POST /api/payments/settlement/imports",
      "POST /api/payments/settlement/batch-runs",
      "POST /api/payments/reconciliation/runs",
      expect.stringMatching(/^GET \/api\/payments\/reconciliation\/exceptions\?/u)
    ]);
    expect(requests[0].body).toMatchObject({ institutionCode: "SYN-CLEARING-HOUSE", requestedBy: "ops01" });
    expect(requests[1].body).toMatchObject({ externalSettlementImportId: importId, feeRateBps: 100, vatRateBps: 1000 });
    expect(requests[2].body).toMatchObject({ externalSettlementImportId: importId, ownerId: "ops01", slaDays: 2 });
  }
});
