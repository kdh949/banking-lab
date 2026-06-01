import { assertTransactionBalanced, projectBalances } from "../../../packages/banking-domain/src/index.mjs";

export function runLedgerInvariantCheck(transactions, accounts) {
  for (const transaction of transactions) {
    assertTransactionBalanced(transaction);
  }
  return {
    checkedTransactions: transactions.length,
    balances: projectBalances(transactions, accounts)
  };
}
