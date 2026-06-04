export interface BankingApiClientOptions {
  readonly baseUrl: string;
  readonly bearerToken?: string;
  readonly fetchImpl?: typeof fetch;
}

export interface StaffAccessItemResponse<T> {
  readonly auditEventId: string;
  readonly item: T;
}

export interface StaffAccessListResponse<T> {
  readonly auditEventId: string;
  readonly items: readonly T[];
}

export interface StaffCustomerDetailDto {
  readonly customerId: string;
  readonly piiExposure: string;
  readonly customerGrade: string;
  readonly riskGrade: string;
  readonly maskedName?: string | null;
  readonly maskedPhone?: string | null;
  readonly maskedAddress?: string | null;
  readonly name?: string | null;
  readonly phone?: string | null;
  readonly address?: string | null;
}

export interface CustomerAccountDetailDto {
  readonly customerId: string;
  readonly accountId: string;
  readonly maskedAccountNo: string;
  readonly status: string;
  readonly currency: string;
  readonly ledgerBalanceMinor: number;
  readonly availableBalanceMinor: number;
  readonly holdAmountMinor: number;
}

export interface CustomerTransferCommand {
  readonly customerId?: string;
  readonly fromAccountId: string;
  readonly toAccountId: string;
  readonly amountMinor: number;
  readonly idempotencyKey: string;
  readonly requestedBy?: string;
  readonly businessDate?: string;
  readonly reason?: string;
  readonly currency?: string;
  readonly businessReferenceId?: string;
  readonly newDevice?: boolean;
  readonly firstTimeBeneficiary?: boolean;
}

export interface CustomerTransferDto {
  readonly resultId?: string | null;
  readonly transactionId?: string | null;
  readonly status: string;
  readonly fromAccountId: string;
  readonly toAccountId: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly idempotencyKey: string;
  readonly caseId?: string | null;
  readonly failureCode?: string | null;
  readonly message?: string | null;
}

export interface CustomerTransferResponse {
  readonly item: CustomerTransferDto;
  readonly replayed: boolean;
}

export interface CustomerTransactionDto {
  readonly transactionId: string;
  readonly transactionType: string;
  readonly status: string;
  readonly businessDate: string;
  readonly postedAt?: string | null;
  readonly accountId: string;
  readonly direction: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly requestedChannel: string;
  readonly reason?: string | null;
}

export interface CustomerTransactionHistoryResponse {
  readonly items: readonly CustomerTransactionDto[];
}

export interface CustomerTransferStatusDto {
  readonly resultId?: string | null;
  readonly transactionId?: string | null;
  readonly caseId?: string | null;
  readonly transferReferenceId?: string | null;
  readonly customerId: string;
  readonly status: string;
  readonly caseStatus?: string | null;
  readonly transferStatus?: string | null;
  readonly fromAccountId?: string | null;
  readonly toAccountId?: string | null;
  readonly amountMinor?: number | null;
  readonly currency?: string | null;
  readonly businessDate?: string | null;
  readonly riskScore?: number | null;
  readonly idempotencyKey?: string | null;
  readonly failureCode?: string | null;
  readonly message?: string | null;
}

export interface CustomerTransferStatusResponse {
  readonly items: readonly CustomerTransferStatusDto[];
}

export interface ComplaintCaseDto {
  readonly caseId: string;
  readonly customerId: string;
  readonly category: string;
  readonly description: string;
  readonly status: string;
  readonly slaDueAt: string;
  readonly classification?: string | null;
  readonly owner?: string | null;
  readonly approvalId?: string | null;
  readonly answer?: ComplaintAnswerDto | null;
  readonly answerDraft?: ComplaintAnswerDraftDto | null;
  readonly customerConfirmedAt?: string | null;
  readonly timeline?: readonly ComplaintTimelineEntryDto[];
}

export interface ComplaintTimelineEntryDto {
  readonly type: string;
  readonly from?: string | null;
  readonly to: string;
  readonly note?: string | null;
  readonly at: string;
}

export interface ComplaintAnswerDto {
  readonly body: string;
  readonly answeredBy: string;
  readonly answeredAt: string;
}

export interface ComplaintAnswerDraftDto {
  readonly body: string;
  readonly draftedBy: string;
  readonly draftedAt: string;
}

export interface CustomerComplaintEntryCommand {
  readonly customerId?: string;
  readonly category: string;
  readonly description: string;
  readonly requestedBy?: string;
  readonly reason?: string;
}

export interface CustomerComplaintEntryResponse {
  readonly item: ComplaintCaseDto;
}

export interface CustomerComplaintListResponse {
  readonly items: readonly ComplaintCaseDto[];
}

export interface CustomerComplaintConfirmCommand {
  readonly customerId?: string;
  readonly note?: string;
  readonly reason?: string;
}

export interface CustomerComplaintConfirmResponse {
  readonly item: ComplaintCaseDto;
}

export interface ComplaintAnswerDraftCommand {
  readonly actorId?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly body?: string;
}

export interface OperatorApproval {
  readonly approvalId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly requestedBy: string;
  readonly requestedAt?: string;
  readonly requestReason: string;
  readonly beforeSnapshot?: Record<string, unknown> | null;
  readonly afterSnapshot?: Record<string, unknown> | null;
  readonly status: string;
  readonly approvedBy?: string | null;
  readonly approvedAt?: string | null;
  readonly rejectedBy?: string | null;
  readonly rejectedAt?: string | null;
  readonly rejectReason?: string | null;
  readonly auditEventId?: string | null;
}

export interface ComplaintAnswerDraftResponse {
  readonly item: ComplaintCaseDto;
  readonly approval: OperatorApproval;
}

export interface ApproveApprovalCommand {
  readonly approvedBy: string;
  readonly approvedByRole?: string;
  readonly screenId?: string;
}

export interface RejectApprovalCommand {
  readonly rejectedBy: string;
  readonly rejectedByRole?: string;
  readonly rejectReason: string;
  readonly screenId?: string;
}

export interface CustomerInfoChangeCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly afterSnapshot?: {
    readonly phone?: string;
    readonly address?: string;
    readonly customerGrade?: string;
  };
}

export interface CustomerInfoChangeResponse {
  readonly item: OperatorApproval;
  readonly customer: StaffCustomerDetailDto;
}

export interface AccountHoldRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly description?: string;
  readonly holdAmountMinor?: number;
  readonly idempotencyKey?: string;
}

export interface AccountHoldReleaseRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly description?: string;
  readonly holdAmountMinor?: number;
  readonly idempotencyKey?: string;
}

export interface AccountHoldRequestDto {
  readonly requestId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly targetCustomerId: string;
  readonly targetAccountId: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly reasonCode: string;
  readonly holdAmountMinor: number;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly metadata: Record<string, unknown>;
}

export interface AccountHoldRequestResponse {
  readonly item: AccountHoldRequestDto;
  readonly approval: OperatorApproval;
  readonly account: CustomerAccountDetailDto;
}

export interface TransferLimitChangeRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly description?: string;
  readonly dailyTransferLimitMinor?: number;
  readonly singleTransferLimitMinor?: number;
  readonly idempotencyKey?: string;
}

export interface StaffTransferLimitDto {
  readonly customerId: string;
  readonly accountId: string;
  readonly maskedAccountNo: string;
  readonly accountStatus: string;
  readonly currency: string;
  readonly dailyTransferLimitMinor: number;
  readonly singleTransferLimitMinor: number;
  readonly updatedAt: string;
}

export interface TransferLimitChangeRequestDto {
  readonly requestId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly targetCustomerId: string;
  readonly targetAccountId: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly reasonCode: string;
  readonly currentDailyTransferLimitMinor: number;
  readonly currentSingleTransferLimitMinor: number;
  readonly requestedDailyTransferLimitMinor: number;
  readonly requestedSingleTransferLimitMinor: number;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly metadata: Record<string, unknown>;
}

export interface TransferLimitChangeRequestResponse {
  readonly item: TransferLimitChangeRequestDto;
  readonly approval: OperatorApproval;
  readonly limit: StaffTransferLimitDto;
}

export interface CustomerKycReviewRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly reviewTrigger?: string;
  readonly description?: string;
  readonly idempotencyKey?: string;
}

export interface StaffKycProfileDto {
  readonly customerId: string;
  readonly kycStatus: string;
  readonly sourceOfFundsCode: string;
  readonly transactionPurposeCode: string;
  readonly simulatedProviderReference: string;
  readonly updatedAt: string;
}

export interface CustomerKycReviewRequestDto {
  readonly requestId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly targetCustomerId: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly reasonCode: string;
  readonly reviewTrigger: string;
  readonly previousKycStatus: string;
  readonly requestedKycStatus: string;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly metadata: Record<string, unknown>;
}

export interface CustomerKycReviewRequestResponse {
  readonly item: CustomerKycReviewRequestDto;
  readonly approval: OperatorApproval;
  readonly kycProfile: StaffKycProfileDto;
}

export interface FeeWaiverRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly feeCode?: string;
  readonly waivedAmountMinor?: number;
  readonly currency?: string;
  readonly targetTransactionId?: string;
  readonly description?: string;
  readonly idempotencyKey?: string;
}

export interface FeeWaiverRequestDto {
  readonly requestId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly targetCustomerId: string;
  readonly targetAccountId: string;
  readonly targetTransactionId?: string | null;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly reasonCode: string;
  readonly feeCode: string;
  readonly waivedAmountMinor: number;
  readonly currency: string;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly metadata: Record<string, unknown>;
}

export interface FeeWaiverRequestResponse {
  readonly item: FeeWaiverRequestDto;
  readonly approval: OperatorApproval;
  readonly account: CustomerAccountDetailDto;
}

export interface TransactionCorrectionRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly reasonCode?: string;
  readonly correctionType?: string;
  readonly targetAccountId?: string;
  readonly businessDate?: string;
  readonly description?: string;
  readonly idempotencyKey?: string;
}

export interface TransactionCorrectionRequestDto {
  readonly requestId: string;
  readonly businessType: string;
  readonly businessReferenceId: string;
  readonly targetCustomerId: string;
  readonly targetAccountId: string;
  readonly targetTransactionId: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly reasonCode: string;
  readonly correctionType: string;
  readonly correctionBusinessDate: string;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly ledgerTransactionId?: string | null;
  readonly idempotencyKey: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly metadata: Record<string, unknown>;
}

export interface TransactionCorrectionRequestResponse {
  readonly item: TransactionCorrectionRequestDto;
  readonly approval: OperatorApproval;
  readonly account: CustomerAccountDetailDto;
}

export interface DepositProductDto {
  readonly productId: string;
  readonly productCode: string;
  readonly productName: string;
  readonly currency: string;
  readonly status: string;
  readonly minimumOpeningBalanceMinor: number;
  readonly currentRateVersionId?: string | null;
  readonly annualRateBps?: number | null;
  readonly rateEffectiveFrom?: string | null;
  readonly rateEffectiveTo?: string | null;
  readonly syntheticOnly: boolean;
}

export interface DepositProductListResponse {
  readonly items: readonly DepositProductDto[];
}

export interface DepositRateChangeRequestCommand {
  readonly requestedAnnualRateBps: number;
  readonly effectiveFrom: string;
  readonly requestedBy: string;
  readonly actorRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface DepositRateChangeRequestDto {
  readonly requestId: string;
  readonly productId: string;
  readonly approvalId?: string | null;
  readonly requestedAnnualRateBps: number;
  readonly effectiveFrom: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly status: string;
  readonly idempotencyKey: string;
  readonly appliedRateVersionId?: string | null;
  readonly createdAt?: string | null;
  readonly updatedAt?: string | null;
  readonly appliedAt?: string | null;
}

export interface DepositRateChangeRequestResponse {
  readonly item: DepositRateChangeRequestDto;
  readonly approval?: OperatorApproval | null;
  readonly replayed: boolean;
}

export interface InterestAccrualRunCommand {
  readonly accrualDate: string;
  readonly requestedBy: string;
  readonly actorRole?: string;
  readonly reason: string;
}

export interface InterestAccrualDto {
  readonly accrualId: string;
  readonly accountId: string;
  readonly productId: string;
  readonly rateVersionId: string;
  readonly accrualDate: string;
  readonly balanceMinor: number;
  readonly annualRateBps: number;
  readonly accruedInterestMinor: number;
  readonly status: string;
  readonly batchId?: string | null;
  readonly ledgerTransactionId?: string | null;
}

export interface InterestAccrualRunResponse {
  readonly accrualDate: string;
  readonly items: readonly InterestAccrualDto[];
  readonly totalInterestMinor: number;
}

export interface InterestPostingBatchCommand {
  readonly businessDate: string;
  readonly requestedBy: string;
  readonly actorRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface InterestPostingBatchDto {
  readonly batchId: string;
  readonly businessDate: string;
  readonly idempotencyKey: string;
  readonly status: string;
  readonly ledgerTransactionId: string;
  readonly totalInterestMinor: number;
  readonly accountCount: number;
  readonly requestedBy: string;
  readonly reason: string;
  readonly postedAt?: string | null;
}

export interface InterestPostingBatchResponse {
  readonly item: InterestPostingBatchDto;
  readonly ledgerTransaction?: LedgerTransactionDto | null;
  readonly replayed: boolean;
}

export interface PiiUnmaskCommand {
  readonly customerId: string;
  readonly requestedBy?: string;
  readonly actorRole?: string;
  readonly reason?: string;
  readonly screenId?: string;
}

export interface StaffUnmaskResponse {
  readonly auditEventId: string;
  readonly expiresInSeconds: number;
  readonly item: StaffCustomerDetailDto;
}

export interface StaffApprovalExecutionResponse {
  readonly item: OperatorApproval;
  readonly executed: boolean;
  readonly customer?: StaffCustomerDetailDto | null;
  readonly account?: CustomerAccountDetailDto | null;
  readonly accountHoldRequest?: AccountHoldRequestDto | null;
  readonly transferLimit?: StaffTransferLimitDto | null;
  readonly transferLimitChangeRequest?: TransferLimitChangeRequestDto | null;
  readonly kycProfile?: StaffKycProfileDto | null;
  readonly kycReviewRequest?: CustomerKycReviewRequestDto | null;
  readonly feeWaiverRequest?: FeeWaiverRequestDto | null;
  readonly transactionCorrectionRequest?: TransactionCorrectionRequestDto | null;
  readonly depositRateChangeRequest?: DepositRateChangeRequestDto | null;
  readonly complaint?: ComplaintCaseDto | null;
  readonly fdsCase?: FdsCaseDto | null;
  readonly amlCase?: AmlCaseDto | null;
  readonly reconciliationItem?: ReconciliationItemDto | null;
  readonly ledgerTransaction?: LedgerCommandResult | null;
}

export interface StaffApprovalRejectionResponse {
  readonly item: OperatorApproval;
  readonly rejected: boolean;
  readonly feeWaiverRequest?: FeeWaiverRequestDto | null;
  readonly transactionCorrectionRequest?: TransactionCorrectionRequestDto | null;
  readonly depositRateChangeRequest?: DepositRateChangeRequestDto | null;
}

export interface ReconciliationItemsResponse {
  readonly items: readonly ReconciliationItemDto[];
}

export interface ReconciliationItemDto {
  readonly itemId: string;
  readonly businessDate: string;
  readonly sourceSystem: string;
  readonly internalReferenceId?: string | null;
  readonly externalReferenceId?: string | null;
  readonly amountMinor: number;
  readonly currency: string;
  readonly status: string;
  readonly owner?: string | null;
  readonly approvalId?: string | null;
}

export interface ReconciliationAdjustmentCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly accountId?: string;
  readonly direction?: "DEBIT" | "CREDIT";
  readonly amountMinor?: number;
  readonly businessDate?: string;
  readonly idempotencyKey?: string;
}

export interface ReconciliationAdjustmentRequestResponse {
  readonly item: ReconciliationItemDto;
  readonly approval: OperatorApproval;
}

export interface AuditEventListResponse {
  readonly hashChainValid: boolean;
  readonly items: readonly AuditEventDto[];
}

export interface AuditEventDto {
  readonly auditEventId: string;
  readonly eventType: string;
  readonly actorType: string;
  readonly actorId: string;
  readonly actorRole: string;
  readonly screenId?: string | null;
  readonly businessReferenceId?: string | null;
  readonly customerId?: string | null;
  readonly accountId?: string | null;
  readonly reason?: string | null;
  readonly payloadHash: string;
  readonly previousEventHash?: string | null;
  readonly createdAt: string;
}

export interface AdminPlatformControlDto {
  readonly controlId: string;
  readonly status: string;
  readonly evidence: string;
}

export interface AdminPlatformSummaryResponse {
  readonly syntheticOnly: boolean;
  readonly nodeReferenceRuntimeRetained: boolean;
  readonly migrationTarget: string;
  readonly controls: readonly AdminPlatformControlDto[];
}

export interface FdsCaseDto {
  readonly caseId: string;
  readonly transferReferenceId: string;
  readonly customerId: string;
  readonly status: string;
  readonly riskScore: number;
  readonly alerts: readonly RiskAlertDto[];
  readonly owner?: string | null;
  readonly approvalId?: string | null;
  readonly amountMinor?: number | null;
  readonly transferStatus?: string | null;
}

export interface FdsDecisionCommand {
  readonly actorId?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
}

export interface FdsAssignCommand {
  readonly actorId?: string;
  readonly actorRole?: string;
  readonly owner?: string;
  readonly reason?: string;
}

export interface FdsDecisionRequestResponse {
  readonly item: FdsCaseDto;
  readonly approval: OperatorApproval;
}

export interface LedgerPostingDto {
  readonly id: string;
  readonly ledgerTransactionId: string;
  readonly accountId: string;
  readonly currency: string;
  readonly direction: "DEBIT" | "CREDIT";
  readonly amountMinor: number;
  readonly postingType: string;
  readonly createdAt?: string | null;
}

export interface LedgerTransactionDto {
  readonly id: string;
  readonly transactionType: string;
  readonly businessReferenceId?: string;
  readonly idempotencyKey?: string;
  readonly businessDate?: string;
  readonly status: string;
  readonly requestedBy?: string;
  readonly requestedChannel?: string;
  readonly postedAt?: string | null;
  readonly originalTransactionId?: string | null;
  readonly postings?: readonly LedgerPostingDto[];
}

export interface LedgerCommandResult {
  readonly value: LedgerTransactionDto;
  readonly replayed: boolean;
}

export interface AmlCaseDto {
  readonly caseId: string;
  readonly customerId: string;
  readonly status: string;
  readonly riskScore: number;
  readonly alerts: readonly RiskAlertDto[];
  readonly owner?: string | null;
  readonly approvalId?: string | null;
  readonly comments: readonly AmlCommentDto[];
}

export interface AmlClosureCommand {
  readonly actorId?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly disposition?: string;
  readonly reportReferenceId?: string | null;
}

export interface AmlClosureRequestResponse {
  readonly item: AmlCaseDto;
  readonly approval: OperatorApproval;
}

export interface RiskAlertDto {
  readonly ruleId: string;
  readonly message: string;
}

export interface AmlCommentDto {
  readonly actorId: string;
  readonly body: string;
  readonly createdAt: string;
}

export class BankingApiError extends Error {
  readonly status: number;
  readonly body: string;

  constructor(status: number, body: string) {
    super(`Banking API request failed with HTTP ${status}`);
    this.name = "BankingApiError";
    this.status = status;
    this.body = body;
  }
}

export function createBankingApiClient(options: BankingApiClientOptions) {
  const baseUrl = normalizeBaseUrl(options.baseUrl);
  const fetchImpl = options.fetchImpl ?? globalThis.fetch?.bind(globalThis);
  if (!fetchImpl) {
    throw new Error("A fetch implementation is required for Banking API calls.");
  }

  return {
    staffCustomerDetail(customerId: string, reason: string) {
      return request<StaffAccessItemResponse<StaffCustomerDetailDto>>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/${encodeURIComponent(customerId)}/detail`,
        { reason },
        options.bearerToken
      );
    },

    requestCustomerInfoChange(customerId: string, command: CustomerInfoChangeCommand) {
      return request<CustomerInfoChangeResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/${encodeURIComponent(customerId)}/change-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestAccountHold(accountId: string, command: AccountHoldRequestCommand) {
      return request<AccountHoldRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/${encodeURIComponent(accountId)}/hold-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestAccountHoldRelease(accountId: string, command: AccountHoldReleaseRequestCommand) {
      return request<AccountHoldRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/${encodeURIComponent(accountId)}/hold-release-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    staffTransferLimits(customerId: string, reason: string) {
      return request<StaffAccessListResponse<StaffTransferLimitDto>>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/${encodeURIComponent(customerId)}/transfer-limits`,
        { reason },
        options.bearerToken
      );
    },

    requestTransferLimitChange(accountId: string, command: TransferLimitChangeRequestCommand) {
      return request<TransferLimitChangeRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/${encodeURIComponent(accountId)}/limit-change-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestCustomerKycReview(customerId: string, command: CustomerKycReviewRequestCommand) {
      return request<CustomerKycReviewRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/${encodeURIComponent(customerId)}/kyc-review-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestFeeWaiver(accountId: string, command: FeeWaiverRequestCommand) {
      return request<FeeWaiverRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/${encodeURIComponent(accountId)}/fee-waiver-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestTransactionCorrection(transactionId: string, command: TransactionCorrectionRequestCommand) {
      return request<TransactionCorrectionRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/transactions/${encodeURIComponent(transactionId)}/correction-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    depositProducts(asOf?: string) {
      return request<DepositProductListResponse>(
        fetchImpl,
        baseUrl,
        "/api/products/deposits",
        asOf ? { asOf } : {},
        options.bearerToken
      );
    },

    depositProduct(productId: string, asOf?: string) {
      return request<DepositProductDto>(
        fetchImpl,
        baseUrl,
        `/api/products/deposits/${encodeURIComponent(productId)}`,
        asOf ? { asOf } : {},
        options.bearerToken
      );
    },

    requestDepositRateChange(productId: string, command: DepositRateChangeRequestCommand) {
      return request<DepositRateChangeRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/products/deposits/${encodeURIComponent(productId)}/rate-change-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    runInterestAccrual(command: InterestAccrualRunCommand) {
      return request<InterestAccrualRunResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/interest-accruals/run",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    postInterestBatch(command: InterestPostingBatchCommand) {
      return request<InterestPostingBatchResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/interest-posting-batches",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    unmaskStaffCustomer(command: PiiUnmaskCommand) {
      return request<StaffUnmaskResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/pii/unmask",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    customerAccountDetail(accountId: string, customerId: string) {
      return request<CustomerAccountDetailDto>(
        fetchImpl,
        baseUrl,
        `/api/customer/accounts/${encodeURIComponent(accountId)}/detail`,
        { customerId },
        options.bearerToken
      );
    },

    requestCustomerTransfer(command: CustomerTransferCommand) {
      return request<CustomerTransferResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/transfers",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    customerTransactions(customerId: string, accountId: string) {
      return request<CustomerTransactionHistoryResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/transactions",
        { customerId, accountId },
        options.bearerToken
      );
    },

    customerTransfers(customerId: string) {
      return request<CustomerTransferStatusResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/transfers",
        { customerId },
        options.bearerToken
      );
    },

    requestCustomerComplaint(command: CustomerComplaintEntryCommand) {
      return request<CustomerComplaintEntryResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/complaints",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    customerComplaints(customerId: string) {
      return request<CustomerComplaintListResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/complaints",
        { customerId },
        options.bearerToken
      );
    },

    confirmCustomerComplaint(caseId: string, command: CustomerComplaintConfirmCommand) {
      return request<CustomerComplaintConfirmResponse>(
        fetchImpl,
        baseUrl,
        `/api/customer/complaints/${encodeURIComponent(caseId)}/confirm`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    complaintCases() {
      return request<ComplaintCaseDto[]>(
        fetchImpl,
        baseUrl,
        "/api/staff/complaints",
        {},
        options.bearerToken
      );
    },

    draftComplaintAnswer(caseId: string, command: ComplaintAnswerDraftCommand) {
      return request<ComplaintAnswerDraftResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/complaints/${encodeURIComponent(caseId)}/answer-drafts`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    staffApprovals() {
      return request<readonly OperatorApproval[]>(
        fetchImpl,
        baseUrl,
        "/api/approvals",
        {},
        options.bearerToken
      );
    },

    staffApproval(approvalId: string) {
      return request<OperatorApproval>(
        fetchImpl,
        baseUrl,
        `/api/approvals/${encodeURIComponent(approvalId)}`,
        {},
        options.bearerToken
      );
    },

    approveStaffApproval(approvalId: string, command: ApproveApprovalCommand) {
      return request<StaffApprovalExecutionResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/approvals/${encodeURIComponent(approvalId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectStaffApproval(approvalId: string, command: RejectApprovalCommand) {
      return request<StaffApprovalRejectionResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/approvals/${encodeURIComponent(approvalId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reconciliationItems() {
      return request<ReconciliationItemsResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/reconciliation-items",
        {},
        options.bearerToken
      );
    },

    requestReconciliationAdjustment(itemId: string, command: ReconciliationAdjustmentCommand) {
      return request<ReconciliationAdjustmentRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/reconciliation-items/${encodeURIComponent(itemId)}/adjustment-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    auditEvents() {
      return request<AuditEventListResponse>(
        fetchImpl,
        baseUrl,
        "/api/audit/events",
        {},
        options.bearerToken
      );
    },

    adminPlatformSummary() {
      return request<AdminPlatformSummaryResponse>(
        fetchImpl,
        baseUrl,
        "/api/admin/platform/summary",
        {},
        options.bearerToken
      );
    },

    fdsCases() {
      return request<FdsCaseDto[]>(
        fetchImpl,
        baseUrl,
        "/api/staff/fds-cases",
        {},
        options.bearerToken
      );
    },

    assignFdsCase(caseId: string, command: FdsAssignCommand) {
      return request<FdsCaseDto>(
        fetchImpl,
        baseUrl,
        `/api/staff/fds-cases/${encodeURIComponent(caseId)}/assign`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestFdsRelease(caseId: string, command: FdsDecisionCommand) {
      return request<FdsDecisionRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/fds-cases/${encodeURIComponent(caseId)}/release-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestFdsBlock(caseId: string, command: FdsDecisionCommand) {
      return request<FdsDecisionRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/fds-cases/${encodeURIComponent(caseId)}/block-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    amlCases() {
      return request<AmlCaseDto[]>(
        fetchImpl,
        baseUrl,
        "/api/staff/aml-cases",
        {},
        options.bearerToken
      );
    },

    requestAmlClosure(caseId: string, command: AmlClosureCommand) {
      return request<AmlClosureRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/aml-cases/${encodeURIComponent(caseId)}/closure-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    }
  };
}

async function request<T>(
  fetchImpl: typeof fetch,
  baseUrl: string,
  path: string,
  searchParams: Record<string, string>,
  bearerToken: string | undefined,
  options: { readonly method?: "GET" | "POST"; readonly body?: unknown } = {}
): Promise<T> {
  const url = new URL(path, baseUrl);
  for (const [key, value] of Object.entries(searchParams)) {
    url.searchParams.set(key, value);
  }
  const response = await fetchImpl(url.toString(), {
    method: options.method ?? "GET",
    headers: {
      Accept: "application/json",
      ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(bearerToken ? { Authorization: bearerToken } : {})
    },
    ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) })
  });
  const body = await response.text();
  if (!response.ok) {
    throw new BankingApiError(response.status, body);
  }
  return JSON.parse(body) as T;
}

function normalizeBaseUrl(baseUrl: string): string {
  const trimmed = baseUrl.trim();
  if (!trimmed) {
    throw new Error("Banking API base URL is required.");
  }
  return trimmed.endsWith("/") ? trimmed : `${trimmed}/`;
}
