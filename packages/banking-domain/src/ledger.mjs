const FINAL_STATUSES = new Set(["POSTED", "REVERSED"]);

function nowIso() {
  return new Date().toISOString();
}

function assertPositiveMinorUnit(amountMinor) {
  if (!Number.isInteger(amountMinor) || amountMinor <= 0) {
    throw new Error("amountMinor must be a positive integer minor-unit value");
  }
}

export function signedPostingAmount(posting) {
  if (posting.direction === "DEBIT") {
    return -posting.amountMinor;
  }
  if (posting.direction === "CREDIT") {
    return posting.amountMinor;
  }
  throw new Error(`Unsupported posting direction: ${posting.direction}`);
}

export function assertTransactionBalanced(transaction) {
  const totals = new Map();
  for (const posting of transaction.postings || []) {
    assertPositiveMinorUnit(posting.amountMinor);
    if (!posting.accountId) {
      throw new Error("posting.accountId is required");
    }
    const currency = posting.currency || "KRW";
    totals.set(currency, (totals.get(currency) || 0) + signedPostingAmount(posting));
  }
  for (const [currency, total] of totals.entries()) {
    if (total !== 0) {
      throw new Error(`ledger transaction ${transaction.id} is not balanced for ${currency}: ${total}`);
    }
  }
  if ((transaction.postings || []).length < 2) {
    throw new Error("ledger transaction must contain at least two postings");
  }
  return true;
}

export function createLedgerTransaction(input) {
  if (!input.id) {
    throw new Error("ledger transaction id is required");
  }
  if (!input.idempotencyKey) {
    throw new Error("idempotencyKey is required for externally retried commands");
  }
  const transaction = {
    id: input.id,
    transactionType: input.transactionType,
    businessReferenceId: input.businessReferenceId || input.id,
    idempotencyKey: input.idempotencyKey,
    status: input.status || "POSTED",
    requestedBy: input.requestedBy || "SYSTEM",
    requestedChannel: input.requestedChannel || "SYSTEM",
    createdAt: input.createdAt || nowIso(),
    postedAt: input.postedAt || nowIso(),
    originalTransactionId: input.originalTransactionId || null,
    metadata: input.metadata || {},
    postings: (input.postings || []).map((posting, index) => ({
      id: posting.id || `${input.id}-P${String(index + 1).padStart(3, "0")}`,
      ledgerTransactionId: input.id,
      accountId: posting.accountId,
      currency: posting.currency || "KRW",
      direction: posting.direction,
      amountMinor: posting.amountMinor,
      postingType: posting.postingType || "PRINCIPAL",
      createdAt: posting.createdAt || nowIso()
    }))
  };
  assertTransactionBalanced(transaction);
  return transaction;
}

export function createInternalTransfer(input) {
  assertPositiveMinorUnit(input.amountMinor);
  return createLedgerTransaction({
    id: input.id,
    transactionType: "INTERNAL_TRANSFER",
    businessReferenceId: input.businessReferenceId,
    idempotencyKey: input.idempotencyKey,
    requestedBy: input.requestedBy,
    requestedChannel: input.requestedChannel,
    metadata: {
      description: input.description || "Synthetic internal transfer"
    },
    postings: [
      {
        accountId: input.fromAccountId,
        direction: "DEBIT",
        amountMinor: input.amountMinor,
        currency: input.currency || "KRW"
      },
      {
        accountId: input.toAccountId,
        direction: "CREDIT",
        amountMinor: input.amountMinor,
        currency: input.currency || "KRW"
      }
    ]
  });
}

export function createReversalTransaction(originalTransaction, input) {
  if (!originalTransaction?.id) {
    throw new Error("original transaction is required");
  }
  if (!input.idempotencyKey) {
    throw new Error("idempotencyKey is required for reversal");
  }
  return createLedgerTransaction({
    id: input.id,
    transactionType: "REVERSAL",
    businessReferenceId: input.businessReferenceId || `${originalTransaction.businessReferenceId}-REV`,
    idempotencyKey: input.idempotencyKey,
    requestedBy: input.requestedBy,
    requestedChannel: input.requestedChannel,
    originalTransactionId: originalTransaction.id,
    metadata: {
      reverses: originalTransaction.id,
      reason: input.reason
    },
    postings: originalTransaction.postings.map((posting) => ({
      accountId: posting.accountId,
      direction: posting.direction === "DEBIT" ? "CREDIT" : "DEBIT",
      amountMinor: posting.amountMinor,
      currency: posting.currency,
      postingType: "REVERSAL"
    }))
  });
}

export function projectBalances(transactions, accounts = []) {
  const balances = new Map();
  for (const account of accounts) {
    balances.set(account.accountId, {
      accountId: account.accountId,
      currency: account.currency || "KRW",
      ledgerBalanceMinor: 0,
      availableBalanceMinor: 0,
      holdAmountMinor: account.holdAmountMinor || 0,
      lastPostingId: null
    });
  }
  for (const transaction of transactions) {
    if (!FINAL_STATUSES.has(transaction.status)) {
      continue;
    }
    assertTransactionBalanced(transaction);
    for (const posting of transaction.postings) {
      const current = balances.get(posting.accountId) || {
        accountId: posting.accountId,
        currency: posting.currency || "KRW",
        ledgerBalanceMinor: 0,
        availableBalanceMinor: 0,
        holdAmountMinor: 0,
        lastPostingId: null
      };
      current.ledgerBalanceMinor += signedPostingAmount(posting);
      current.availableBalanceMinor = current.ledgerBalanceMinor - current.holdAmountMinor;
      current.lastPostingId = posting.id;
      balances.set(posting.accountId, current);
    }
  }
  for (const balance of balances.values()) {
    balance.availableBalanceMinor = balance.ledgerBalanceMinor - balance.holdAmountMinor;
    if (balance.availableBalanceMinor > balance.ledgerBalanceMinor) {
      throw new Error(`available balance exceeds ledger balance for ${balance.accountId}`);
    }
  }
  return Array.from(balances.values());
}

export class IdempotencyStore {
  constructor(seed = []) {
    this.records = new Map(seed.map((item) => [item.key, item.value]));
  }

  run(key, factory) {
    if (!key) {
      throw new Error("idempotency key is required");
    }
    if (this.records.has(key)) {
      return {
        value: this.records.get(key),
        replayed: true
      };
    }
    const value = factory();
    this.records.set(key, value);
    return {
      value,
      replayed: false
    };
  }
}
