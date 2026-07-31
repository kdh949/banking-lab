export type ExternalSettlementImportStatus = "IMPORTED" | "BATCHED";
export type ExternalSettlementLineStatus = "ACCEPTED" | "REJECTED" | "RETURNED";
export type SettlementBatchStatus = "INCLUDED_IN_BATCH";
export type PaymentReconciliationRunStatus = "EVIDENCE_PENDING" | "MATCHED" | "EXCEPTIONS_OPEN" | "FAILED";
export type PaymentReconciliationMismatchType =
  | "MATCHED"
  | "MISSING_PAYMENT"
  | "MISSING_LEDGER"
  | "MISSING_EXTERNAL"
  | "AMOUNT_MISMATCH"
  | "STATUS_MISMATCH"
  | "DUPLICATE_EXTERNAL"
  | "VALUE_DATE_MISMATCH"
  | "LATE_SETTLEMENT";
export type PaymentReconciliationResultStatus = "MATCHED" | "OPEN" | "INVESTIGATING" | "RESOLVED";

export interface ImportExternalSettlementCsvRequest {
  readonly originalFileName: string;
  readonly institutionCode: string;
  readonly businessDate: string;
  readonly csvContent: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface ExternalSettlementLineDto {
  readonly externalSettlementLineId: string;
  readonly lineNumber: number;
  readonly externalReference: string;
  readonly paymentInstructionId: string;
  readonly billerId: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly businessDate: string;
  readonly valueDate: string;
  readonly status: ExternalSettlementLineStatus;
  readonly rawLine: string;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
}

export interface ExternalSettlementImportDto {
  readonly externalSettlementImportId: string;
  readonly originalFileName: string;
  readonly institutionCode: string;
  readonly fileSha256: string;
  readonly fileByteSize: number;
  readonly businessDate: string;
  readonly status: ExternalSettlementImportStatus;
  readonly lineCount: number;
  readonly acceptedLineCount: number;
  readonly rejectedLineCount: number;
  readonly requestedBy: string;
  readonly reason: string;
  readonly lines: readonly ExternalSettlementLineDto[];
  readonly syntheticOnly: boolean;
  readonly receivedAt: string;
}

export interface ExternalSettlementImportResponse {
  readonly item: ExternalSettlementImportDto;
  readonly replayed: boolean;
}

export interface CreateSettlementBatchRunRequest {
  readonly externalSettlementImportId: string;
  readonly feeRateBps: number;
  readonly vatRateBps?: number;
  readonly cutoffAt: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface SettlementBatchDto {
  readonly settlementBatchId: string;
  readonly settlementBatchRunId: string;
  readonly externalSettlementImportId: string;
  readonly billerId: string;
  readonly currency: string;
  readonly businessDate: string;
  readonly valueDate: string;
  readonly cutoffAt: string;
  readonly grossAmountMinor: number;
  readonly feeRateBps: number;
  readonly feeAmountMinor: number;
  readonly vatRateBps: number;
  readonly vatAmountMinor: number;
  readonly adjustmentAmountMinor: number;
  readonly netAmountMinor: number;
  readonly itemCount: number;
  readonly status: SettlementBatchStatus;
  readonly externalPayoutReference?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
}

export interface SettlementBatchRunResponse {
  readonly settlementBatchRunId: string;
  readonly externalSettlementImportId: string;
  readonly items: readonly SettlementBatchDto[];
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface CreatePaymentReconciliationRunRequest {
  readonly externalSettlementImportId: string;
  readonly expectedValueDate: string;
  readonly allowedValueDateLagDays?: number;
  readonly slaDays?: number;
  readonly ownerId: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface PaymentReconciliationRunDto {
  readonly paymentReconciliationRunId: string;
  readonly externalSettlementImportId: string;
  readonly businessDate: string;
  readonly expectedValueDate: string;
  readonly allowedValueDateLagDays: number;
  readonly slaDays: number;
  readonly ownerId: string;
  readonly status: PaymentReconciliationRunStatus;
  readonly externalLineCount: number;
  readonly ledgerEvidenceCount: number;
  readonly matchedCount: number;
  readonly exceptionCount: number;
  readonly attemptCount: number;
  readonly failureMessage?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly completedAt?: string | null;
}

export interface PaymentReconciliationResultDto {
  readonly paymentReconciliationResultId: string;
  readonly paymentReconciliationRunId: string;
  readonly externalSettlementLineId?: string | null;
  readonly paymentInstructionId: string;
  readonly ledgerTransactionId?: string | null;
  readonly mismatchType: PaymentReconciliationMismatchType;
  readonly resultStatus: PaymentReconciliationResultStatus;
  readonly internalAmountMinor?: number | null;
  readonly ledgerAmountMinor?: number | null;
  readonly externalAmountMinor?: number | null;
  readonly currency?: string | null;
  readonly externalStatus?: string | null;
  readonly businessDate: string;
  readonly expectedValueDate: string;
  readonly actualValueDate?: string | null;
  readonly ownerId: string;
  readonly detectedReason: string;
  readonly detectedAt: string;
  readonly dueAt?: string | null;
  readonly agingDays: number;
  readonly overdue: boolean;
  readonly resolution?: string | null;
  readonly approvalId?: string | null;
  readonly resolvedAt?: string | null;
  readonly syntheticOnly: boolean;
}

export interface PaymentReconciliationRunResponse {
  readonly item: PaymentReconciliationRunDto;
  readonly results: readonly PaymentReconciliationResultDto[];
  readonly replayed: boolean;
  readonly auditEventId?: string | null;
  readonly syntheticOnly: boolean;
}

export interface PaymentReconciliationExceptionListResponse {
  readonly items: readonly PaymentReconciliationResultDto[];
  readonly auditEventId: string;
  readonly syntheticOnly: boolean;
}

export interface PaymentReconciliationExceptionQuery {
  readonly reason: string;
  readonly ownerId?: string;
  readonly status?: PaymentReconciliationResultStatus;
  readonly overdueOnly?: boolean;
}

type RequestOptions = {
  readonly method?: "GET" | "POST" | "PUT";
  readonly body?: unknown;
};

export interface PaymentSettlementRequester {
  request<T>(
    path: string,
    searchParams?: Record<string, string>,
    options?: RequestOptions
  ): Promise<T>;
}

export function createPaymentSettlementMethods(requester: PaymentSettlementRequester) {
  return {
    importExternalSettlementCsv(command: ImportExternalSettlementCsvRequest) {
      return requester.request<ExternalSettlementImportResponse>(
        "/api/payments/settlement/imports",
        {},
        { method: "POST", body: command }
      );
    },

    externalSettlementImport(importId: string) {
      return requester.request<ExternalSettlementImportResponse>(
        `/api/payments/settlement/imports/${encodeURIComponent(importId)}`
      );
    },

    createSettlementBatchRun(command: CreateSettlementBatchRunRequest) {
      return requester.request<SettlementBatchRunResponse>(
        "/api/payments/settlement/batch-runs",
        {},
        { method: "POST", body: command }
      );
    },

    settlementBatchRun(batchRunId: string) {
      return requester.request<SettlementBatchRunResponse>(
        `/api/payments/settlement/batch-runs/${encodeURIComponent(batchRunId)}`
      );
    },

    createPaymentReconciliationRun(command: CreatePaymentReconciliationRunRequest) {
      return requester.request<PaymentReconciliationRunResponse>(
        "/api/payments/reconciliation/runs",
        {},
        { method: "POST", body: command }
      );
    },

    paymentReconciliationRun(runId: string, reason: string) {
      return requester.request<PaymentReconciliationRunResponse>(
        `/api/payments/reconciliation/runs/${encodeURIComponent(runId)}`,
        { reason }
      );
    },

    paymentReconciliationExceptions(query: PaymentReconciliationExceptionQuery) {
      return requester.request<PaymentReconciliationExceptionListResponse>(
        "/api/payments/reconciliation/exceptions",
        {
          reason: query.reason,
          ...(query.ownerId ? { ownerId: query.ownerId } : {}),
          ...(query.status ? { status: query.status } : {}),
          ...(query.overdueOnly === undefined ? {} : { overdueOnly: String(query.overdueOnly) })
        }
      );
    }
  };
}
