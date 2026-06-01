import { mkdir, writeFile } from "node:fs/promises";
import { generateSyntheticDataset } from "../packages/banking-domain/src/index.mjs";
import { LedgerCore } from "../services/core-banking/src/index.mjs";

const outputDir = "docs/test-evidence/generated";
const outputFile = `${outputDir}/phase-2-ledger-core.json`;
const dataset = generateSyntheticDataset();
const core = new LedgerCore({
  customers: dataset.customers,
  accounts: dataset.accounts,
  transactions: dataset.ledgerTransactions
});

const transfer = await core.transfer({
  fromAccountId: "ACC-SYN-001-001",
  toAccountId: "ACC-SYN-002-001",
  amountMinor: 10000,
  idempotencyKey: "EVIDENCE-P2-TRF-001",
  requestedBy: "customer01",
  requestedChannel: "CUSTOMER_WEB"
});
const replay = await core.transfer({
  fromAccountId: "ACC-SYN-001-001",
  toAccountId: "ACC-SYN-002-001",
  amountMinor: 10000,
  idempotencyKey: "EVIDENCE-P2-TRF-001",
  requestedBy: "customer01",
  requestedChannel: "CUSTOMER_WEB"
});
const reversal = await core.reverseTransaction({
  originalTransactionId: transfer.value.id,
  idempotencyKey: "EVIDENCE-P2-REV-001",
  requestedBy: "branch01",
  requestedChannel: "STAFF_TERMINAL",
  reason: "Evidence reversal"
});

const concurrencyCore = new LedgerCore({
  accounts: [
    { accountId: "BANK-SUSPENSE", customerId: "BANK", accountNo: "LAB-000-000000", status: "ACTIVE", currency: "KRW" },
    { accountId: "ACC-EVIDENCE-CON", customerId: "SYN-CUS-001", accountNo: "LAB-998-000001", status: "ACTIVE", currency: "KRW" }
  ]
});
await concurrencyCore.deposit({
  accountId: "ACC-EVIDENCE-CON",
  amountMinor: 100000,
  idempotencyKey: "EVIDENCE-P2-CON-SEED",
  requestedBy: "seed",
  requestedChannel: "EVIDENCE"
});
const attempts = await Promise.allSettled(Array.from({ length: 120 }, (_, index) => concurrencyCore.withdraw({
  accountId: "ACC-EVIDENCE-CON",
  amountMinor: 1000,
  idempotencyKey: `EVIDENCE-P2-CON-WDR-${String(index).padStart(3, "0")}`,
  requestedBy: "branch01",
  requestedChannel: "STAFF_TERMINAL"
})));
const closedDate = "2026-01-31";
core.closeBusinessDay(closedDate);
let closedDayRejected = false;
try {
  await core.deposit({
    accountId: "ACC-SYN-001-001",
    amountMinor: 1,
    idempotencyKey: "EVIDENCE-P2-CLOSED",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL",
    businessDate: closedDate
  });
} catch {
  closedDayRejected = true;
}

const evidence = {
  generatedAt: new Date().toISOString(),
  scope: "Phase 2 Ledger Core",
  syntheticOnly: true,
  checks: [
    {
      id: "posting-sum-zero",
      status: core.validateInvariants() ? "pass" : "fail",
      detail: `${core.transactions.length} ledger transactions validated as balanced`
    },
    {
      id: "idempotency-retry",
      status: replay.replayed && replay.value.id === transfer.value.id ? "pass" : "fail",
      detail: "Retry with same idempotency key returned the original transfer"
    },
    {
      id: "reversal-reference",
      status: reversal.value.originalTransactionId === transfer.value.id ? "pass" : "fail",
      detail: `Reversal ${reversal.value.id} references ${transfer.value.id}`
    },
    {
      id: "concurrent-withdrawal",
      status: attempts.filter((item) => item.status === "fulfilled").length === 100
        && attempts.filter((item) => item.status === "rejected").length === 20
        && concurrencyCore.getAccountBalance("ACC-EVIDENCE-CON").availableBalanceMinor === 0
        ? "pass"
        : "fail",
      detail: "120 concurrent withdrawals against 100 units allowed 100 successes and 20 rejections"
    },
    {
      id: "closed-day-mutation-guard",
      status: closedDayRejected ? "pass" : "fail",
      detail: "Direct deposit command on closed business day was rejected"
    }
  ]
};

await mkdir(outputDir, { recursive: true });
await writeFile(outputFile, `${JSON.stringify(evidence, null, 2)}\n`);

console.log(`Wrote ${outputFile}`);
