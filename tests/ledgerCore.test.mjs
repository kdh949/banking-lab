import assert from "node:assert/strict";
import test from "node:test";
import { generateSyntheticDataset } from "../legacy-node-reference/packages/banking-domain/src/index.mjs";
import { LedgerCore } from "../legacy-node-reference/services/core-banking/src/index.mjs";

function seededCore() {
  const dataset = generateSyntheticDataset();
  return {
    dataset,
    core: new LedgerCore({
      customers: dataset.customers,
      accounts: dataset.accounts,
      transactions: dataset.ledgerTransactions
    })
  };
}

test("deposit and withdrawal are posted as balanced ledger transactions", async () => {
  const { core } = seededCore();
  const before = core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor;
  const deposit = await core.deposit({
    accountId: "ACC-SYN-001-001",
    amountMinor: 5000,
    idempotencyKey: "CORE-DEP-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL"
  });
  const withdrawal = await core.withdraw({
    accountId: "ACC-SYN-001-001",
    amountMinor: 2000,
    idempotencyKey: "CORE-WDR-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL"
  });
  const after = core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor;

  assert.equal(deposit.value.transactionType, "DEPOSIT");
  assert.equal(withdrawal.value.transactionType, "WITHDRAWAL");
  assert.equal(after, before + 3000);
  assert.equal(core.validateInvariants(), true);
});

test("withdrawal cannot exceed available balance and does not mutate ledger on failure", async () => {
  const { core } = seededCore();
  const beforeCount = core.transactions.length;

  await assert.rejects(() => core.withdraw({
    accountId: "ACC-SYN-001-001",
    amountMinor: 999999999999,
    idempotencyKey: "CORE-WDR-BAD-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL"
  }), /insufficient available balance/);

  assert.equal(core.transactions.length, beforeCount);
  assert.equal(core.validateInvariants(), true);
});

test("internal transfer uses one ledger source of truth for both accounts", async () => {
  const { core } = seededCore();
  const fromBefore = core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor;
  const toBefore = core.getAccountBalance("ACC-SYN-002-001").ledgerBalanceMinor;

  const result = await core.transfer({
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 10000,
    idempotencyKey: "CORE-TRF-001",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });

  assert.equal(result.value.transactionType, "INTERNAL_TRANSFER");
  assert.equal(core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor, fromBefore - 10000);
  assert.equal(core.getAccountBalance("ACC-SYN-002-001").ledgerBalanceMinor, toBefore + 10000);
  assert.equal(core.validateInvariants(), true);
});

test("idempotency retry returns the original transaction only once", async () => {
  const { core } = seededCore();
  const first = await core.transfer({
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 12000,
    idempotencyKey: "CORE-IDEMP-001",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });
  const second = await core.transfer({
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 12000,
    idempotencyKey: "CORE-IDEMP-001",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });

  assert.equal(first.replayed, false);
  assert.equal(second.replayed, true);
  assert.equal(second.value.id, first.value.id);
  assert.equal(core.transactions.filter((transaction) => transaction.idempotencyKey === "CORE-IDEMP-001").length, 1);
});

test("reversal references the original transaction and cannot be duplicated", async () => {
  const { core } = seededCore();
  const fromBefore = core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor;
  const toBefore = core.getAccountBalance("ACC-SYN-002-001").ledgerBalanceMinor;
  const transfer = await core.transfer({
    fromAccountId: "ACC-SYN-001-001",
    toAccountId: "ACC-SYN-002-001",
    amountMinor: 7000,
    idempotencyKey: "CORE-REV-BASE-001",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });
  const reversal = await core.reverseTransaction({
    originalTransactionId: transfer.value.id,
    idempotencyKey: "CORE-REV-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL",
    reason: "Synthetic reversal test"
  });

  await assert.rejects(() => core.reverseTransaction({
    originalTransactionId: transfer.value.id,
    idempotencyKey: "CORE-REV-DUP-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL",
    reason: "Duplicate reversal test"
  }), /already reversed/);

  assert.equal(reversal.value.originalTransactionId, transfer.value.id);
  assert.equal(core.getAccountBalance("ACC-SYN-001-001").ledgerBalanceMinor, fromBefore);
  assert.equal(core.getAccountBalance("ACC-SYN-002-001").ledgerBalanceMinor, toBefore);
  assert.equal(core.validateInvariants(), true);
});

test("concurrent withdrawals are serialized and cannot overdraw an account", async () => {
  const accounts = [
    { accountId: "BANK-SUSPENSE", customerId: "BANK", accountNo: "LAB-000-000000", status: "ACTIVE", currency: "KRW" },
    { accountId: "ACC-CON-001", customerId: "SYN-CUS-001", accountNo: "LAB-999-000001", status: "ACTIVE", currency: "KRW" }
  ];
  const core = new LedgerCore({ accounts });
  await core.deposit({
    accountId: "ACC-CON-001",
    amountMinor: 100000,
    idempotencyKey: "CORE-CON-SEED",
    requestedBy: "seed",
    requestedChannel: "TEST"
  });

  const attempts = await Promise.allSettled(Array.from({ length: 120 }, (_, index) => core.withdraw({
    accountId: "ACC-CON-001",
    amountMinor: 1000,
    idempotencyKey: `CORE-CON-WDR-${String(index).padStart(3, "0")}`,
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL"
  })));
  const fulfilled = attempts.filter((item) => item.status === "fulfilled");
  const rejected = attempts.filter((item) => item.status === "rejected");

  assert.equal(fulfilled.length, 100);
  assert.equal(rejected.length, 20);
  assert.equal(core.getAccountBalance("ACC-CON-001").availableBalanceMinor, 0);
  assert.equal(core.validateInvariants(), true);
});

test("closed business day rejects direct posting commands", async () => {
  const { core } = seededCore();
  const businessDate = "2026-01-31";
  core.closeBusinessDay(businessDate);
  const beforeCount = core.transactions.length;

  await assert.rejects(() => core.deposit({
    accountId: "ACC-SYN-001-001",
    amountMinor: 5000,
    idempotencyKey: "CORE-CLOSED-DEP-001",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL",
    businessDate
  }), /business day is closed/);

  assert.equal(core.transactions.length, beforeCount);
  assert.equal(core.validateInvariants(), true);
});
