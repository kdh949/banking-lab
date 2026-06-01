import { createLedgerTransaction, projectBalances } from "./ledger.mjs";

export function generateSyntheticCustomers() {
  return [
    {
      customerId: "SYN-CUS-001",
      name: "Lab Customer Alpha",
      phone: "010-0000-1001",
      address: "Seoul Synthetic District",
      customerGrade: "STANDARD",
      riskGrade: "LOW"
    },
    {
      customerId: "SYN-CUS-002",
      name: "Lab Customer Beta",
      phone: "010-0000-1002",
      address: "Busan Synthetic Harbor",
      customerGrade: "PREMIER",
      riskGrade: "MEDIUM"
    },
    {
      customerId: "SYN-CUS-003",
      name: "Lab Customer Gamma",
      phone: "010-0000-1003",
      address: "Daegu Synthetic Valley",
      customerGrade: "STANDARD",
      riskGrade: "HIGH"
    }
  ];
}

export function generateSyntheticAccounts() {
  return [
    {
      accountId: "BANK-SUSPENSE",
      customerId: "BANK",
      accountNo: "LAB-000-000000",
      status: "ACTIVE",
      currency: "KRW"
    },
    {
      accountId: "ACC-SYN-001-001",
      customerId: "SYN-CUS-001",
      accountNo: "LAB-001-000001",
      status: "ACTIVE",
      currency: "KRW"
    },
    {
      accountId: "ACC-SYN-002-001",
      customerId: "SYN-CUS-002",
      accountNo: "LAB-002-000001",
      status: "ACTIVE",
      currency: "KRW"
    },
    {
      accountId: "ACC-SYN-003-001",
      customerId: "SYN-CUS-003",
      accountNo: "LAB-003-000001",
      status: "ACTIVE",
      currency: "KRW"
    }
  ];
}

export function generateOpeningLedgerTransactions() {
  const openings = [
    ["TX-OPEN-001", "ACC-SYN-001-001", 100000000],
    ["TX-OPEN-002", "ACC-SYN-002-001", 50000000],
    ["TX-OPEN-003", "ACC-SYN-003-001", 25000000]
  ];
  return openings.map(([id, accountId, amountMinor]) => createLedgerTransaction({
    id,
    transactionType: "SYNTHETIC_OPENING_BALANCE",
    businessReferenceId: id,
    idempotencyKey: `SEED-${id}`,
    requestedBy: "SEED",
    requestedChannel: "SYNTHETIC_DATA_GENERATOR",
    postings: [
      {
        accountId: "BANK-SUSPENSE",
        direction: "DEBIT",
        amountMinor,
        currency: "KRW",
        postingType: "OPENING"
      },
      {
        accountId,
        direction: "CREDIT",
        amountMinor,
        currency: "KRW",
        postingType: "OPENING"
      }
    ]
  }));
}

export function generateSyntheticDataset() {
  const customers = generateSyntheticCustomers();
  const accounts = generateSyntheticAccounts();
  const ledgerTransactions = generateOpeningLedgerTransactions();
  const balances = projectBalances(ledgerTransactions, accounts);
  return {
    generatedAt: new Date().toISOString(),
    notice: "Synthetic data only. Do not use real customer PII or real payment rails.",
    customers,
    accounts,
    ledgerTransactions,
    balances
  };
}
