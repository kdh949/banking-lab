import {
  IdempotencyStore,
  assertTransactionBalanced,
  createInternalTransfer,
  createLedgerTransaction,
  createReversalTransaction,
  projectBalances
} from "../../../packages/banking-domain/src/index.mjs";

const BANK_SUSPENSE_ACCOUNT_ID = "BANK-SUSPENSE";

function today() {
  return new Date().toISOString().slice(0, 10);
}

function assertPositiveAmount(amountMinor) {
  if (!Number.isInteger(amountMinor) || amountMinor <= 0) {
    throw new Error("amountMinor must be a positive integer minor-unit value");
  }
}

class CommandLock {
  constructor() {
    this.tail = Promise.resolve();
  }

  async run(fn) {
    const previous = this.tail;
    let release;
    this.tail = new Promise((resolve) => {
      release = resolve;
    });
    await previous;
    try {
      return await fn();
    } finally {
      release();
    }
  }
}

export class LedgerCore {
  constructor(input = {}) {
    this.customers = [...(input.customers || [])];
    this.accounts = [...(input.accounts || [])];
    this.transactions = [...(input.transactions || [])];
    this.idempotencyStore = input.idempotencyStore || new IdempotencyStore();
    this.closedBusinessDates = new Set(input.closedBusinessDates || []);
    this.commandLock = new CommandLock();
  }

  listCustomers() {
    return this.customers.map((customer) => ({ ...customer }));
  }

  listAccounts(customerId) {
    return this.accounts
      .filter((account) => !customerId || account.customerId === customerId)
      .map((account) => ({ ...account }));
  }

  listTransactions(accountId) {
    return this.transactions
      .filter((transaction) => !accountId || transaction.postings.some((posting) => posting.accountId === accountId))
      .map((transaction) => ({
        ...transaction,
        postings: transaction.postings.map((posting) => ({ ...posting }))
      }));
  }

  getBalances() {
    return projectBalances(this.transactions, this.accounts);
  }

  getAccountBalance(accountId) {
    const balance = this.getBalances().find((item) => item.accountId === accountId);
    if (!balance) {
      throw new Error(`account not found: ${accountId}`);
    }
    return balance;
  }

  closeBusinessDay(businessDate) {
    if (!businessDate) {
      throw new Error("businessDate is required");
    }
    this.closedBusinessDates.add(businessDate);
    return {
      businessDate,
      status: "CLOSED"
    };
  }

  assertBusinessDateOpen(businessDate) {
    if (this.closedBusinessDates.has(businessDate)) {
      throw new Error(`business day is closed: ${businessDate}`);
    }
  }

  requireAccount(accountId) {
    const account = this.accounts.find((item) => item.accountId === accountId);
    if (!account) {
      throw new Error(`account not found: ${accountId}`);
    }
    if (account.status !== "ACTIVE") {
      throw new Error(`account is not active: ${accountId}`);
    }
    return account;
  }

  nextTransactionId(prefix) {
    return `${prefix}-${String(this.transactions.length + 1).padStart(8, "0")}`;
  }

  async deposit(input) {
    return this.commandLock.run(async () => {
      const businessDate = input.businessDate || today();
      this.assertBusinessDateOpen(businessDate);
      assertPositiveAmount(input.amountMinor);
      this.requireAccount(input.accountId);
      const result = this.idempotencyStore.run(input.idempotencyKey, () => {
        const transaction = createLedgerTransaction({
          id: input.id || this.nextTransactionId("TX-DEP"),
          transactionType: "DEPOSIT",
          businessReferenceId: input.businessReferenceId,
          idempotencyKey: input.idempotencyKey,
          requestedBy: input.requestedBy || "SYSTEM",
          requestedChannel: input.requestedChannel || "CORE_BANKING",
          businessDate,
          metadata: {
            businessDate,
            description: input.description || "Synthetic deposit"
          },
          postings: [
            {
              accountId: BANK_SUSPENSE_ACCOUNT_ID,
              direction: "DEBIT",
              amountMinor: input.amountMinor,
              currency: input.currency || "KRW"
            },
            {
              accountId: input.accountId,
              direction: "CREDIT",
              amountMinor: input.amountMinor,
              currency: input.currency || "KRW"
            }
          ]
        });
        this.transactions.push(transaction);
        return transaction;
      });
      this.validateInvariants();
      return result;
    });
  }

  async withdraw(input) {
    return this.commandLock.run(async () => {
      const businessDate = input.businessDate || today();
      this.assertBusinessDateOpen(businessDate);
      assertPositiveAmount(input.amountMinor);
      this.requireAccount(input.accountId);
      const result = this.idempotencyStore.run(input.idempotencyKey, () => {
        const balance = this.getAccountBalance(input.accountId);
        if (balance.availableBalanceMinor < input.amountMinor) {
          throw new Error(`insufficient available balance for ${input.accountId}`);
        }
        const transaction = createLedgerTransaction({
          id: input.id || this.nextTransactionId("TX-WDR"),
          transactionType: "WITHDRAWAL",
          businessReferenceId: input.businessReferenceId,
          idempotencyKey: input.idempotencyKey,
          requestedBy: input.requestedBy || "SYSTEM",
          requestedChannel: input.requestedChannel || "CORE_BANKING",
          businessDate,
          metadata: {
            businessDate,
            description: input.description || "Synthetic withdrawal"
          },
          postings: [
            {
              accountId: input.accountId,
              direction: "DEBIT",
              amountMinor: input.amountMinor,
              currency: input.currency || "KRW"
            },
            {
              accountId: BANK_SUSPENSE_ACCOUNT_ID,
              direction: "CREDIT",
              amountMinor: input.amountMinor,
              currency: input.currency || "KRW"
            }
          ]
        });
        this.transactions.push(transaction);
        return transaction;
      });
      this.validateInvariants();
      return result;
    });
  }

  async transfer(input) {
    return this.commandLock.run(async () => {
      const businessDate = input.businessDate || today();
      this.assertBusinessDateOpen(businessDate);
      assertPositiveAmount(input.amountMinor);
      this.requireAccount(input.fromAccountId);
      this.requireAccount(input.toAccountId);
      if (input.fromAccountId === input.toAccountId) {
        throw new Error("fromAccountId and toAccountId must differ");
      }
      const result = this.idempotencyStore.run(input.idempotencyKey, () => {
        const balance = this.getAccountBalance(input.fromAccountId);
        if (balance.availableBalanceMinor < input.amountMinor) {
          throw new Error(`insufficient available balance for ${input.fromAccountId}`);
        }
        const transaction = createInternalTransfer({
          id: input.id || this.nextTransactionId("TX-TRF"),
          fromAccountId: input.fromAccountId,
          toAccountId: input.toAccountId,
          amountMinor: input.amountMinor,
          currency: input.currency || "KRW",
          businessReferenceId: input.businessReferenceId,
          idempotencyKey: input.idempotencyKey,
          requestedBy: input.requestedBy || "SYSTEM",
          requestedChannel: input.requestedChannel || "CORE_BANKING",
          businessDate,
          description: input.description || "Synthetic internal transfer"
        });
        transaction.businessDate = businessDate;
        transaction.metadata = {
          ...transaction.metadata,
          businessDate
        };
        this.transactions.push(transaction);
        return transaction;
      });
      this.validateInvariants();
      return result;
    });
  }

  async reverseTransaction(input) {
    return this.commandLock.run(async () => {
      const businessDate = input.businessDate || today();
      this.assertBusinessDateOpen(businessDate);
      const original = this.transactions.find((transaction) => transaction.id === input.originalTransactionId);
      if (!original) {
        throw new Error(`original transaction not found: ${input.originalTransactionId}`);
      }
      if (original.transactionType === "REVERSAL") {
        throw new Error("reversal transactions cannot be reversed directly");
      }
      const existingReversal = this.transactions.find((transaction) => transaction.originalTransactionId === original.id);
      if (existingReversal && existingReversal.idempotencyKey !== input.idempotencyKey) {
        throw new Error(`transaction already reversed: ${original.id}`);
      }
      const result = this.idempotencyStore.run(input.idempotencyKey, () => {
        if (existingReversal) {
          return existingReversal;
        }
        const reversal = createReversalTransaction(original, {
          id: input.id || this.nextTransactionId("TX-REV"),
          businessReferenceId: input.businessReferenceId,
          idempotencyKey: input.idempotencyKey,
          requestedBy: input.requestedBy || "SYSTEM",
          requestedChannel: input.requestedChannel || "CORE_BANKING",
          businessDate,
          reason: input.reason
        });
        reversal.businessDate = businessDate;
        reversal.metadata = {
          ...reversal.metadata,
          businessDate
        };
        this.transactions.push(reversal);
        return reversal;
      });
      this.validateInvariants();
      return result;
    });
  }

  validateInvariants() {
    const idempotencyKeys = new Set();
    const transactionIds = new Set();
    for (const transaction of this.transactions) {
      if (transactionIds.has(transaction.id)) {
        throw new Error(`duplicate ledger transaction id: ${transaction.id}`);
      }
      transactionIds.add(transaction.id);
      if (idempotencyKeys.has(transaction.idempotencyKey)) {
        throw new Error(`duplicate ledger transaction idempotency key: ${transaction.idempotencyKey}`);
      }
      idempotencyKeys.add(transaction.idempotencyKey);
      assertTransactionBalanced(transaction);
      if (transaction.status === "PARTIALLY_POSTED") {
        throw new Error(`partial finalization is not allowed: ${transaction.id}`);
      }
      if (transaction.transactionType === "REVERSAL") {
        if (!transaction.originalTransactionId) {
          throw new Error(`reversal missing original transaction reference: ${transaction.id}`);
        }
        if (!this.transactions.some((item) => item.id === transaction.originalTransactionId)) {
          throw new Error(`reversal references missing original transaction: ${transaction.id}`);
        }
      }
    }
    projectBalances(this.transactions, this.accounts);
    return true;
  }
}
