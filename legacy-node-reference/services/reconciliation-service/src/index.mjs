import { assertTransactionBalanced, projectBalances } from "../../../../packages/banking-domain/src/index.mjs";

export function runLedgerInvariantCheck(transactions, accounts) {
  for (const transaction of transactions) {
    assertTransactionBalanced(transaction);
  }
  return {
    checkedTransactions: transactions.length,
    balances: projectBalances(transactions, accounts)
  };
}

function nowIso() {
  return new Date().toISOString();
}

function postingAmount(transaction) {
  return (transaction.postings || []).find((posting) => posting.direction === "DEBIT")?.amountMinor || 0;
}

function internalTransferEntries(transactions, businessDate) {
  return transactions
    .filter((transaction) => transaction.status === "POSTED")
    .filter((transaction) => !businessDate || transaction.businessDate === businessDate)
    .filter((transaction) => transaction.transactionType === "INTERNAL_TRANSFER")
    .map((transaction) => ({
      referenceId: transaction.id,
      businessDate: transaction.businessDate,
      amountMinor: postingAmount(transaction),
      status: transaction.status,
      transactionType: transaction.transactionType
    }));
}

function totals(entries) {
  return entries.reduce((sum, entry) => sum + entry.amountMinor, 0);
}

function makeItem(input) {
  return {
    itemId: input.itemId,
    businessDate: input.businessDate,
    status: "OPEN",
    owner: input.owner || "ops01",
    mismatchType: input.mismatchType,
    referenceId: input.referenceId,
    internalAmountMinor: input.internalAmountMinor || 0,
    externalAmountMinor: input.externalAmountMinor || 0,
    reason: input.reason,
    approvalId: null,
    adjustmentTransactionId: null,
    createdAt: nowIso(),
    timeline: [
      {
        from: "RECONCILED",
        to: "OPEN",
        type: "UNMATCHED_ITEM_CREATED",
        actorId: "RECONCILIATION_ENGINE",
        at: nowIso()
      }
    ]
  };
}

export function runDailyReconciliation(input) {
  const businessDate = input.businessDate;
  const transactions = input.transactions || [];
  const accounts = input.accounts || [];
  const externalEntries = input.externalEntries || [];
  runLedgerInvariantCheck(transactions, accounts);

  const internalEntries = internalTransferEntries(transactions, businessDate);
  const externalByReference = new Map(externalEntries.map((entry) => [entry.referenceId, entry]));
  const itemSeed = input.itemSeed || 1;
  const items = [];

  for (const internal of internalEntries) {
    const external = externalByReference.get(internal.referenceId);
    if (!external) {
      items.push(makeItem({
        itemId: `REC-${String(itemSeed + items.length).padStart(8, "0")}`,
        businessDate,
        mismatchType: "MISSING_EXTERNAL",
        referenceId: internal.referenceId,
        internalAmountMinor: internal.amountMinor,
        externalAmountMinor: 0,
        reason: "Internal ledger transaction missing from external file"
      }));
    } else if (external.amountMinor !== internal.amountMinor || external.status !== "SETTLED") {
      items.push(makeItem({
        itemId: `REC-${String(itemSeed + items.length).padStart(8, "0")}`,
        businessDate,
        mismatchType: "AMOUNT_OR_STATUS_MISMATCH",
        referenceId: internal.referenceId,
        internalAmountMinor: internal.amountMinor,
        externalAmountMinor: external.amountMinor,
        reason: "External file amount or status differs from internal ledger"
      }));
    }
  }

  const internalReferences = new Set(internalEntries.map((entry) => entry.referenceId));
  for (const external of externalEntries) {
    if (!internalReferences.has(external.referenceId)) {
      items.push(makeItem({
        itemId: `REC-${String(itemSeed + items.length).padStart(8, "0")}`,
        businessDate,
        mismatchType: "UNMATCHED_EXTERNAL",
        referenceId: external.referenceId,
        internalAmountMinor: 0,
        externalAmountMinor: external.amountMinor,
        reason: "External file entry does not exist in internal ledger"
      }));
    }
  }

  return {
    closing: {
      closingId: input.closingId,
      businessDate,
      status: items.length > 0 ? "UNMATCHED" : "MATCHED",
      ledgerInvariantValid: true,
      internalEntryCount: internalEntries.length,
      externalEntryCount: externalEntries.length,
      unmatchedItemCount: items.length,
      internalTotalMinor: totals(internalEntries),
      externalTotalMinor: totals(externalEntries),
      closedAt: nowIso()
    },
    internalEntries,
    externalEntries,
    items
  };
}

export function requestReconciliationAdjustment(item, input = {}) {
  return {
    ...item,
    status: "ADJUSTMENT_REQUESTED",
    approvalId: input.approvalId,
    adjustmentRequest: {
      accountId: input.accountId,
      direction: input.direction || "CREDIT",
      amountMinor: input.amountMinor,
      requestedBy: input.actorId || "ops01",
      requestedAt: nowIso(),
      reason: input.reason
    },
    timeline: [
      ...(item.timeline || []),
      {
        from: item.status,
        to: "ADJUSTMENT_REQUESTED",
        type: "ADJUSTMENT_REQUESTED",
        actorId: input.actorId || "ops01",
        approvalId: input.approvalId,
        at: nowIso()
      }
    ]
  };
}

export function markReconciliationAdjusted(item, input = {}) {
  return {
    ...item,
    status: "ADJUSTED",
    adjustmentTransactionId: input.transactionId,
    timeline: [
      ...(item.timeline || []),
      {
        from: item.status,
        to: "ADJUSTED",
        type: "ADJUSTED",
        actorId: input.actorId || "SYSTEM",
        transactionId: input.transactionId,
        at: nowIso()
      }
    ]
  };
}
