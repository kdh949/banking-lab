import assert from "node:assert/strict";
import test from "node:test";
import {
  IdempotencyStore,
  assertTransactionBalanced,
  createInternalTransfer,
  createLedgerTransaction,
  createReversalTransaction,
  projectBalances
} from "../packages/banking-domain/src/index.mjs";

test("internal transfer creates balanced double-entry postings", () => {
  const transaction = createInternalTransfer({
    id: "TX-TEST-001",
    fromAccountId: "ACC-A",
    toAccountId: "ACC-B",
    amountMinor: 1000,
    idempotencyKey: "IDEMP-001",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });

  assert.equal(assertTransactionBalanced(transaction), true);
  assert.equal(transaction.postings.length, 2);
});

test("balance projection is derived from postings", () => {
  const transaction = createInternalTransfer({
    id: "TX-TEST-002",
    fromAccountId: "ACC-A",
    toAccountId: "ACC-B",
    amountMinor: 2500,
    idempotencyKey: "IDEMP-002",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });

  const balances = projectBalances([transaction], [
    { accountId: "ACC-A", currency: "KRW" },
    { accountId: "ACC-B", currency: "KRW" }
  ]);

  assert.equal(balances.find((item) => item.accountId === "ACC-A").ledgerBalanceMinor, -2500);
  assert.equal(balances.find((item) => item.accountId === "ACC-B").ledgerBalanceMinor, 2500);
});

test("idempotency key creates at most one transaction value", () => {
  const store = new IdempotencyStore();
  const first = store.run("IDEMP-003", () => createInternalTransfer({
    id: "TX-TEST-003",
    fromAccountId: "ACC-A",
    toAccountId: "ACC-B",
    amountMinor: 500,
    idempotencyKey: "IDEMP-003",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  }));
  const second = store.run("IDEMP-003", () => {
    throw new Error("factory must not run on replay");
  });

  assert.equal(first.replayed, false);
  assert.equal(second.replayed, true);
  assert.equal(second.value.id, "TX-TEST-003");
});

test("reversal references original transaction and restores projected balances", () => {
  const original = createInternalTransfer({
    id: "TX-TEST-004",
    fromAccountId: "ACC-A",
    toAccountId: "ACC-B",
    amountMinor: 750,
    idempotencyKey: "IDEMP-004",
    requestedBy: "customer01",
    requestedChannel: "CUSTOMER_WEB"
  });
  const reversal = createReversalTransaction(original, {
    id: "TX-TEST-004-R",
    idempotencyKey: "IDEMP-004-R",
    requestedBy: "branch01",
    requestedChannel: "STAFF_TERMINAL",
    reason: "Synthetic reversal test"
  });

  const balances = projectBalances([original, reversal], [
    { accountId: "ACC-A", currency: "KRW" },
    { accountId: "ACC-B", currency: "KRW" }
  ]);

  assert.equal(reversal.originalTransactionId, original.id);
  assert.equal(balances.find((item) => item.accountId === "ACC-A").ledgerBalanceMinor, 0);
  assert.equal(balances.find((item) => item.accountId === "ACC-B").ledgerBalanceMinor, 0);
});

test("unbalanced ledger transaction is rejected", () => {
  assert.throws(() => createLedgerTransaction({
    id: "TX-BAD-001",
    transactionType: "BAD",
    businessReferenceId: "BAD",
    idempotencyKey: "BAD-001",
    requestedBy: "test",
    requestedChannel: "test",
    postings: [
      {
        accountId: "ACC-A",
        direction: "DEBIT",
        amountMinor: 100,
        currency: "KRW"
      },
      {
        accountId: "ACC-B",
        direction: "CREDIT",
        amountMinor: 90,
        currency: "KRW"
      }
    ]
  }), /not balanced/);
});
