import { createBankingApiClient, type BankingApiClientOptions } from "../client";
import { selectClientMethods } from "./select-client-methods";

export type { BankingApiClientOptions, LedgerTransactionDto, ReconciliationItemDto } from "../client";

const operationsMethodNames = [
  "staffOperationalRetryQueue",
  "staffWorkflowTimeline",
  "reconciliationItems",
  "requestReconciliationAdjustment",
  "startLedgerProjectionDriftRun",
  "ledgerProjectionDriftRun",
  "requestLedgerProjectionRebuild",
  "approveLedgerProjectionRebuildRequest",
  "rejectLedgerProjectionRebuildRequest",
  "executeLedgerProjectionRebuild"
] as const;

export function createOperationsApiClient(options: BankingApiClientOptions) {
  return selectClientMethods(createBankingApiClient(options), operationsMethodNames);
}

export type OperationsApiClient = ReturnType<typeof createOperationsApiClient>;
