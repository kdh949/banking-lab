export interface BankingApiClientOptions {
  readonly baseUrl: string;
  readonly bearerToken?: string;
  readonly fetchImpl?: typeof fetch;
}

export interface CustomerSignupCommand {
  readonly idempotencyKey: string;
  readonly username: string;
  readonly password: string;
  readonly syntheticCustomerName: string;
  readonly syntheticPhone?: string;
  readonly syntheticAddress?: string;
  readonly customerGrade?: string;
  readonly riskGrade?: string;
  readonly sourceOfFundsCode?: string;
  readonly transactionPurposeCode?: string;
}

export interface CustomerLoginCommand {
  readonly username: string;
  readonly password: string;
}

export interface CustomerAuthCustomerDto {
  readonly customerId: string;
  readonly authSubject: string;
  readonly username: string;
  readonly kycStatus: string;
  readonly onboardingStatus?: string;
  readonly duplicateCheckStatus?: string;
  readonly nextRequiredAction?: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerAuthSessionDto {
  readonly subject: string;
  readonly customerId: string;
  readonly roles: readonly string[];
  readonly issuer: string;
  readonly sessionId: string;
  readonly authTime: string;
  readonly issuedAt: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerAuthResponse {
  readonly customer: CustomerAuthCustomerDto;
  readonly session: CustomerAuthSessionDto;
  readonly bearerToken: string;
  readonly tokenType: "Bearer" | string;
  readonly expiresAt: string;
  readonly replayed: boolean;
  readonly onboardingStatus?: string;
  readonly duplicateCheckStatus?: string;
  readonly nextRequiredAction?: string;
  readonly syntheticOnly: boolean;
}

export interface StaffAccessItemResponse<T> {
  readonly auditEventId: string;
  readonly item: T;
}

export interface StaffAccessListResponse<T> {
  readonly auditEventId: string;
  readonly items: readonly T[];
}

export interface OperationalRetryQueueItemDto {
  readonly outboxEventId: string;
  readonly aggregateType: string;
  readonly aggregateId: string;
  readonly eventType: string;
  readonly status: string;
  readonly retryCount: number;
  readonly nextRetryAt?: string | null;
  readonly createdAt: string;
  readonly publishedAt?: string | null;
  readonly errorMessage?: string | null;
  readonly retryEligible: boolean;
}

export interface StaffWorkflowTimelineEntryDto {
  readonly timelineEntryId: string;
  readonly sourceType: string;
  readonly eventType: string;
  readonly status?: string | null;
  readonly actorId?: string | null;
  readonly actorRole?: string | null;
  readonly screenId?: string | null;
  readonly businessReferenceId: string;
  readonly reason?: string | null;
  readonly occurredAt: string;
}

export interface CallCenterCustomerSummaryDto {
  readonly customerId: string;
  readonly maskedName: string;
  readonly maskedPhone?: string | null;
  readonly customerGrade: string;
  readonly riskGrade: string;
}

export interface CallCenterCustomerSearchResponse {
  readonly auditEventId: string;
  readonly items: readonly CallCenterCustomerSummaryDto[];
}

export interface StartCallCenterInteractionCommand {
  readonly customerId?: string | null;
  readonly accountId?: string | null;
  readonly channel?: string | null;
  readonly contactReasonCode?: string | null;
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly assignedTo?: string | null;
  readonly reason?: string | null;
  readonly metadata?: Record<string, unknown> | null;
}

export interface CallCenterNoteCommand {
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly noteBody?: string | null;
}

export interface CallCenterAftercallTaskCommand {
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly taskType?: string | null;
  readonly assignedTo?: string | null;
  readonly dueAt?: string | null;
  readonly metadata?: Record<string, unknown> | null;
}

export interface CallCenterEscalationCommand {
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly escalationType?: string | null;
  readonly complaintCategory?: string | null;
  readonly complaintDescription?: string | null;
  readonly metadata?: Record<string, unknown> | null;
}

export interface CloseCallCenterInteractionCommand {
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
}

export interface CallCenterNoteDto {
  readonly noteId: string;
  readonly interactionId: string;
  readonly customerId: string;
  readonly createdBy: string;
  readonly createdByRole: string;
  readonly noteBodyRedacted: string;
  readonly redactionApplied: boolean;
  readonly piiPatternCount: number;
  readonly reason: string;
  readonly auditEventId: string;
  readonly createdAt: string;
}

export interface CallCenterAftercallTaskDto {
  readonly taskId: string;
  readonly interactionId: string;
  readonly customerId: string;
  readonly taskType: string;
  readonly status: string;
  readonly assignedTo?: string | null;
  readonly dueAt?: string | null;
  readonly createdBy: string;
  readonly createdByRole: string;
  readonly reason: string;
  readonly auditEventId: string;
  readonly metadata: Record<string, unknown>;
  readonly createdAt: string;
}

export interface CallCenterEscalationDto {
  readonly escalationId: string;
  readonly interactionId: string;
  readonly customerId: string;
  readonly escalationType: string;
  readonly status: string;
  readonly complaintCaseId?: string | null;
  readonly requestedBy: string;
  readonly requestedByRole: string;
  readonly reason: string;
  readonly approvalId?: string | null;
  readonly auditEventId: string;
  readonly metadata: Record<string, unknown>;
  readonly createdAt: string;
}

export interface CallCenterInteractionDto {
  readonly interactionId: string;
  readonly customerId: string;
  readonly accountId?: string | null;
  readonly channel: string;
  readonly contactReasonCode: string;
  readonly status: string;
  readonly createdBy: string;
  readonly createdByRole: string;
  readonly assignedTo?: string | null;
  readonly reason: string;
  readonly startedAt: string;
  readonly endedAt?: string | null;
  readonly metadata: Record<string, unknown>;
  readonly auditEventId: string;
  readonly notes: readonly CallCenterNoteDto[];
  readonly aftercallTasks: readonly CallCenterAftercallTaskDto[];
  readonly escalations: readonly CallCenterEscalationDto[];
}

export interface CallCenterInteractionResponse {
  readonly item: CallCenterInteractionDto;
}

export interface CallCenterNoteResponse {
  readonly item: CallCenterInteractionDto;
  readonly note: CallCenterNoteDto;
}

export interface CallCenterAftercallTaskResponse {
  readonly item: CallCenterInteractionDto;
  readonly task: CallCenterAftercallTaskDto;
}

export interface CallCenterEscalationResponse {
  readonly item: CallCenterInteractionDto;
  readonly escalation: CallCenterEscalationDto;
  readonly approval?: OperatorApproval | null;
}

export interface CallCenterInteractionSummaryDto {
  readonly interactionId: string;
  readonly customerId: string;
  readonly maskedCustomerName: string;
  readonly channel: string;
  readonly contactReasonCode: string;
  readonly status: string;
  readonly assignedTo?: string | null;
  readonly startedAt: string;
  readonly endedAt?: string | null;
}

export interface CallCenterInteractionListResponse {
  readonly auditEventId: string;
  readonly items: readonly CallCenterInteractionSummaryDto[];
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
  readonly openedAt?: string | null;
  readonly limits?: CustomerAccountLimitsDto | null;
  readonly holds?: readonly CustomerAccountHoldDto[];
  readonly recentTransactions?: readonly CustomerRecentLedgerActivityDto[];
  readonly statementActions?: readonly CustomerStatementActionDto[];
  readonly syntheticOnly?: boolean;
  readonly maskingPolicy?: string;
}

export interface CustomerAccountListItemDto extends CustomerAccountDetailDto {
  readonly syntheticOnly: boolean;
}

export interface CustomerAccountListResponse {
  readonly items: readonly CustomerAccountListItemDto[];
  readonly syntheticOnly: boolean;
}

export interface CustomerAccountLimitsDto {
  readonly dailyTransferLimitMinor: number;
  readonly singleTransferLimitMinor: number;
  readonly updatedAt?: string | null;
}

export interface CustomerAccountHoldDto {
  readonly holdId: string;
  readonly holdAmountMinor: number;
  readonly reasonCode: string;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly createdAt: string;
}

export interface CustomerStatementActionDto {
  readonly actionType: string;
  readonly href: string;
  readonly ownershipEnforced: boolean;
  readonly syntheticOnly: boolean;
}

export interface CustomerRecentLedgerActivityDto {
  readonly transactionId: string;
  readonly transactionType: string;
  readonly businessDate: string;
  readonly postedAt?: string | null;
  readonly accountId: string;
  readonly maskedAccountNo?: string | null;
  readonly direction: string;
  readonly amountMinor: number;
  readonly signedAmountMinor: number;
  readonly currency: string;
  readonly postingType: string;
  readonly requestedChannel: string;
}

export interface CustomerOnboardingCheckDto {
  readonly checkId: string;
  readonly customerId: string;
  readonly checkType: string;
  readonly status: string;
  readonly riskLevel: string;
  readonly evidence: Record<string, unknown>;
  readonly createdAt: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerProfileDto {
  readonly customerId: string;
  readonly authSubject: string;
  readonly username: string;
  readonly maskedCustomerName: string;
  readonly maskedPhone?: string | null;
  readonly maskedAddress?: string | null;
  readonly customerGrade: string;
  readonly riskGrade: string;
  readonly kycStatus: string;
  readonly onboardingStatus: string;
  readonly duplicateCheckStatus: string;
  readonly nextRequiredAction: string;
  readonly lastLoginAt?: string | null;
  readonly authIdentityStatus: string;
  readonly onboardingChecks: readonly CustomerOnboardingCheckDto[];
  readonly syntheticOnly: boolean;
  readonly maskingPolicy: string;
}

export interface CustomerSelfServiceAccountOpeningCommand {
  readonly idempotencyKey: string;
  readonly productCode?: string;
  readonly accountAlias?: string | null;
  readonly currency?: string;
  readonly syntheticInitialDepositAmountMinor?: number;
  readonly termsAccepted: boolean;
}

export interface CustomerSelfServiceAccountOpeningRequestDto {
  readonly requestId: string;
  readonly idempotencyKey: string;
  readonly status: string;
  readonly customerId: string;
  readonly approvalId?: string | null;
  readonly approvalStatus?: string | null;
  readonly staffAccountOpeningRequestId?: string | null;
  readonly requestedProductCode: string;
  readonly requestedAccountAlias?: string | null;
  readonly requestedCurrency: string;
  readonly requestedInitialDepositAmountMinor: number;
  readonly generatedAccountId?: string | null;
  readonly generatedMaskedAccountNo?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerSelfServiceAccountOpeningRequestResponse {
  readonly item: CustomerSelfServiceAccountOpeningRequestDto;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface CustomerSelfServiceAccountOpeningRequestListResponse {
  readonly items: readonly CustomerSelfServiceAccountOpeningRequestDto[];
  readonly syntheticOnly: boolean;
}

export interface Customer360AccountDto {
  readonly accountId: string;
  readonly maskedAccountNo: string;
  readonly status: string;
  readonly currency: string;
  readonly ledgerBalanceMinor: number;
  readonly availableBalanceMinor: number;
  readonly holdAmountMinor: number;
  readonly openedAt?: string | null;
  readonly statementActions: readonly CustomerStatementActionDto[];
  readonly syntheticOnly: boolean;
}

export interface Customer360AccountSummaryDto {
  readonly totalAccounts: number;
  readonly activeAccounts: number;
  readonly totalLedgerBalanceMinor: number;
  readonly totalAvailableBalanceMinor: number;
  readonly totalHoldAmountMinor: number;
  readonly currency: string;
}

export interface Customer360StatusSummaryDto {
  readonly totalCount: number;
  readonly statusCounts: Record<string, number>;
  readonly source: string;
  readonly syntheticOnly: boolean;
}

export interface Customer360AccessHistorySummaryDto {
  readonly totalEvents: number;
  readonly recentEventTypes: readonly string[];
  readonly lastAccessAt?: string | null;
}

export interface Customer360AvailableActionDto {
  readonly actionType: string;
  readonly enabled: boolean;
  readonly reason?: string | null;
  readonly href?: string | null;
}

export interface Customer360SourceWatermarkDto {
  readonly source: string;
  readonly lastUpdatedAt?: string | null;
  readonly rowCount: number;
}

export interface Customer360Dto {
  readonly profile: CustomerProfileDto;
  readonly kycSummary: Customer360StatusSummaryDto;
  readonly accountSummary: Customer360AccountSummaryDto;
  readonly accounts: readonly Customer360AccountDto[];
  readonly loanSummary: Customer360StatusSummaryDto;
  readonly cardSummary: Customer360StatusSummaryDto;
  readonly complaintSummary: Customer360StatusSummaryDto;
  readonly paymentSummary: Customer360StatusSummaryDto;
  readonly notificationSummary: Customer360StatusSummaryDto;
  readonly recentLedgerActivity: readonly CustomerRecentLedgerActivityDto[];
  readonly accessHistorySummary: Customer360AccessHistorySummaryDto;
  readonly availableActions: readonly Customer360AvailableActionDto[];
  readonly sourceWatermarks: readonly Customer360SourceWatermarkDto[];
  readonly syntheticOnly: boolean;
  readonly maskingPolicy: string;
}

export interface InternalRecipientAccountDto {
  readonly accountId: string;
  readonly maskedAccountNo: string;
  readonly status: string;
  readonly currency: string;
  readonly recipientLabel: string;
  readonly internalOnly: boolean;
  readonly syntheticOnly: boolean;
}

export interface InternalRecipientLookupResponse {
  readonly item: InternalRecipientAccountDto;
  readonly syntheticOnly: boolean;
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

export interface StatementLineDto {
  readonly transactionId: string;
  readonly transactionType: string;
  readonly businessDate: string;
  readonly postedAt?: string | null;
  readonly accountId: string;
  readonly direction: "DEBIT" | "CREDIT";
  readonly amountMinor: number;
  readonly signedAmountMinor: number;
  readonly currency: string;
  readonly postingType: string;
  readonly requestedChannel: string;
  readonly reason?: string | null;
}

export interface CustomerStatementDto {
  readonly statementId?: string | null;
  readonly statementScope?: string;
  readonly accountId?: string | null;
  readonly customerId: string;
  readonly from: string;
  readonly to: string;
  readonly currency: string;
  readonly openingBalanceMinor: number;
  readonly closingBalanceMinor: number;
  readonly debitTotalMinor: number;
  readonly creditTotalMinor: number;
  readonly netAmountMinor: number;
  readonly lineCount: number;
  readonly lines: readonly StatementLineDto[];
  readonly sourceLedgerHash?: string | null;
  readonly payloadHash?: string | null;
  readonly generatedAt?: string | null;
  readonly maskingPolicy?: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerStatementArtifactDto {
  readonly statementId: string;
  readonly customerId: string;
  readonly accountId?: string | null;
  readonly from: string;
  readonly to: string;
  readonly statementScope: string;
  readonly sourceLedgerHash: string;
  readonly payloadHash: string;
  readonly createdAt: string;
  readonly lastViewedAt: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerStatementArtifactListResponse {
  readonly items: readonly CustomerStatementArtifactDto[];
  readonly syntheticOnly: boolean;
}

export interface TransactionConfirmationPostingDto {
  readonly accountId: string;
  readonly customerId: string;
  readonly direction: "DEBIT" | "CREDIT";
  readonly amountMinor: number;
  readonly signedAmountMinor: number;
  readonly currency: string;
  readonly postingType: string;
}

export interface TransactionConfirmationDto {
  readonly confirmationId: string;
  readonly transactionId: string;
  readonly transactionType: string;
  readonly businessReferenceId: string;
  readonly businessDate: string;
  readonly status: string;
  readonly requestedBy: string;
  readonly requestedChannel: string;
  readonly postedAt?: string | null;
  readonly originalTransactionId?: string | null;
  readonly currency: string;
  readonly totalDebitMinor: number;
  readonly totalCreditMinor: number;
  readonly balanced: boolean;
  readonly postings: readonly TransactionConfirmationPostingDto[];
  readonly syntheticOnly: boolean;
}

export interface BalanceCertificateDto {
  readonly certificateId: string;
  readonly accountId: string;
  readonly customerId: string;
  readonly currency: string;
  readonly date: string;
  readonly balanceAsOfMinor: number;
  readonly currentLedgerBalanceMinor: number;
  readonly currentAvailableBalanceMinor: number;
  readonly deterministicInputHash: string;
  readonly sourcePostingCount: number;
  readonly sourceLastBusinessDate?: string | null;
  readonly sourceLedgerHash: string;
  readonly snapshotCreatedAt: string;
  readonly lastViewedAt: string;
  readonly syntheticOnly: boolean;
}

export interface CustomerAccessHistoryItemDto {
  readonly auditEventId: string;
  readonly eventType: string;
  readonly actorType: string;
  readonly actorId: string;
  readonly actorRole: string;
  readonly screenId?: string | null;
  readonly businessReferenceId?: string | null;
  readonly accountId?: string | null;
  readonly reasonPresent: boolean;
  readonly createdAt: string;
}

export interface CustomerAccessHistoryDto {
  readonly customerId: string;
  readonly items: readonly CustomerAccessHistoryItemDto[];
  readonly syntheticOnly: boolean;
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
  readonly sourceReference?: ComplaintSourceReferenceDto | null;
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
  readonly sourceReference?: ComplaintSourceReferenceDto | null;
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

export interface CustomerComplaintMaterialCommand {
  readonly customerId?: string;
  readonly materialType: string;
  readonly fileName: string;
  readonly description?: string | null;
  readonly syntheticStorageRef?: string | null;
  readonly reason?: string | null;
}

export interface ComplaintMaterialDto {
  readonly materialId: string;
  readonly caseId: string;
  readonly customerId: string;
  readonly materialType: string;
  readonly fileName: string;
  readonly description?: string | null;
  readonly syntheticStorageRef: string;
  readonly submittedBy: string;
  readonly createdAt: string;
}

export interface CustomerComplaintMaterialResponse {
  readonly item: ComplaintCaseDto;
  readonly material: ComplaintMaterialDto;
}

export interface CustomerComplaintReopenCommand {
  readonly customerId?: string;
  readonly reopenReason: string;
  readonly reason?: string | null;
}

export interface ComplaintReopenRequestDto {
  readonly reopenRequestId: string;
  readonly caseId: string;
  readonly customerId: string;
  readonly reopenReason: string;
  readonly status: string;
  readonly requestedBy: string;
  readonly createdAt: string;
}

export interface CustomerComplaintReopenResponse {
  readonly item: ComplaintCaseDto;
  readonly reopenRequest: ComplaintReopenRequestDto;
}

export interface ComplaintTypeGuideDto {
  readonly category: string;
  readonly description: string;
  readonly slaHours: number;
  readonly requiredMaterials: readonly string[];
  readonly sourceReferenceTypes?: readonly string[];
}

export interface ComplaintTypeGuideResponse {
  readonly items: readonly ComplaintTypeGuideDto[];
}

export interface ComplaintSourceReferenceDto {
  readonly sourceType: string;
  readonly sourceId: string;
  readonly accountId?: string | null;
  readonly cardId?: string | null;
  readonly ledgerTransactionId?: string | null;
  readonly amountMinor?: number | null;
  readonly currency?: string | null;
  readonly businessDate?: string | null;
  readonly syntheticOnly?: boolean;
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

export interface CustomerOnboardingRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly idempotencyKey: string;
  readonly customerName: string;
  readonly customerPhone: string;
  readonly customerAddress: string;
  readonly customerGrade?: string;
  readonly riskGrade?: string;
  readonly sourceOfFundsCode?: string;
  readonly transactionPurposeCode?: string;
  readonly username: string;
  readonly temporaryPassword: string;
}

export interface CustomerOnboardingApproveCommand {
  readonly approvedBy: string;
  readonly approvedByRole?: string;
  readonly screenId?: string;
}

export interface CustomerOnboardingRejectCommand {
  readonly rejectedBy: string;
  readonly rejectedByRole?: string;
  readonly rejectReason: string;
  readonly screenId?: string;
}

export interface CustomerOnboardingExecuteCommand {
  readonly executedBy: string;
  readonly executedByRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface CustomerOnboardingRequestDto {
  readonly requestId: string;
  readonly idempotencyKey: string;
  readonly status: "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "EXECUTED" | "FAILED" | string;
  readonly requestedBy: string;
  readonly requestedByRole: string;
  readonly reason: string;
  readonly approvalId: string;
  readonly requestedCustomerName: string;
  readonly requestedCustomerPhone: string;
  readonly requestedCustomerAddress: string;
  readonly requestedCustomerGrade: string;
  readonly requestedRiskGrade: string;
  readonly requestedSourceOfFundsCode: string;
  readonly requestedTransactionPurposeCode: string;
  readonly requestedUsername: string;
  readonly generatedCustomerId?: string | null;
  readonly generatedAuthSubject?: string | null;
  readonly approvedBy?: string | null;
  readonly approvedAt?: string | null;
  readonly rejectedBy?: string | null;
  readonly rejectedAt?: string | null;
  readonly rejectReason?: string | null;
  readonly executedBy?: string | null;
  readonly executedByRole?: string | null;
  readonly executedAt?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreatedSyntheticCustomerDto {
  readonly customerId: string;
  readonly authSubject: string;
  readonly username: string;
  readonly kycStatus: string;
}

export interface CustomerOnboardingRequestResponse {
  readonly item: CustomerOnboardingRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface CustomerOnboardingReviewResponse {
  readonly item: CustomerOnboardingRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface CustomerOnboardingExecuteResponse {
  readonly item: CustomerOnboardingRequestDto;
  readonly approval: OperatorApproval;
  readonly customer?: CreatedSyntheticCustomerDto | null;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface AccountOpeningRequestCommand {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly idempotencyKey: string;
  readonly customerId: string;
  readonly productCode?: string;
  readonly accountAlias?: string | null;
  readonly currency?: string;
  readonly dailyTransferLimitMinor?: number;
  readonly singleTransferLimitMinor?: number;
  readonly initialDepositAmountMinor?: number;
  readonly initialDepositIdempotencyKey?: string | null;
  readonly businessDate?: string | null;
}

export interface AccountOpeningApproveCommand {
  readonly approvedBy: string;
  readonly approvedByRole?: string;
  readonly screenId?: string;
}

export interface AccountOpeningRejectCommand {
  readonly rejectedBy: string;
  readonly rejectedByRole?: string;
  readonly rejectReason: string;
  readonly screenId?: string;
}

export interface AccountOpeningExecuteCommand {
  readonly executedBy: string;
  readonly executedByRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface AccountOpeningRequestDto {
  readonly requestId: string;
  readonly idempotencyKey: string;
  readonly status: "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "EXECUTED" | "FAILED" | string;
  readonly requestedBy: string;
  readonly requestedByRole: string;
  readonly reason: string;
  readonly approvalId: string;
  readonly customerId: string;
  readonly requestedProductCode: string;
  readonly requestedAccountAlias?: string | null;
  readonly requestedCurrency: string;
  readonly requestedDailyTransferLimitMinor: number;
  readonly requestedSingleTransferLimitMinor: number;
  readonly requestedInitialDepositAmountMinor: number;
  readonly requestedInitialDepositIdempotencyKey?: string | null;
  readonly requestedBusinessDate?: string | null;
  readonly generatedAccountId?: string | null;
  readonly generatedMaskedAccountNo?: string | null;
  readonly approvedBy?: string | null;
  readonly approvedAt?: string | null;
  readonly rejectedBy?: string | null;
  readonly rejectedAt?: string | null;
  readonly rejectReason?: string | null;
  readonly executedBy?: string | null;
  readonly executedByRole?: string | null;
  readonly executedAt?: string | null;
  readonly initialDepositLedgerTransactionId?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreatedSyntheticAccountDto {
  readonly customerId: string;
  readonly accountId: string;
  readonly maskedAccountNo: string;
  readonly currency: string;
  readonly ledgerBalanceMinor: number;
  readonly availableBalanceMinor: number;
  readonly initialDepositLedgerTransactionId?: string | null;
}

export interface AccountOpeningRequestResponse {
  readonly item: AccountOpeningRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface AccountOpeningReviewResponse {
  readonly item: AccountOpeningRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
}

export interface AccountOpeningExecuteResponse {
  readonly item: AccountOpeningRequestDto;
  readonly approval: OperatorApproval;
  readonly account?: CreatedSyntheticAccountDto | null;
  readonly replayed: boolean;
  readonly syntheticOnly: boolean;
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
  readonly refundLedgerTransactionId?: string | null;
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

export interface FeePolicyDto {
  readonly policyId: string;
  readonly feeCode: string;
  readonly feeName: string;
  readonly productId?: string | null;
  readonly currency: string;
  readonly status: string;
  readonly waiverEligible: boolean;
  readonly currentVersionId?: string | null;
  readonly amountMinor?: number | null;
  readonly effectiveFrom?: string | null;
  readonly effectiveTo?: string | null;
  readonly syntheticOnly: boolean;
}

export interface FeePolicyListResponse {
  readonly items: readonly FeePolicyDto[];
}

export interface StaffFeePolicyListResponse {
  readonly auditEventId: string;
  readonly items: readonly FeePolicyDto[];
}

export interface FeePolicyChangeRequestCommand {
  readonly requestedAmountMinor: number;
  readonly effectiveFrom: string;
  readonly requestedBy: string;
  readonly actorRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface FeePolicyChangeRequestDto {
  readonly requestId: string;
  readonly policyId: string;
  readonly approvalId?: string | null;
  readonly requestedAmountMinor: number;
  readonly effectiveFrom: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly status: string;
  readonly idempotencyKey: string;
  readonly appliedFeePolicyVersionId?: string | null;
  readonly createdAt?: string | null;
  readonly updatedAt?: string | null;
  readonly appliedAt?: string | null;
}

export interface FeePolicyChangeRequestResponse {
  readonly item: FeePolicyChangeRequestDto;
  readonly approval?: OperatorApproval | null;
  readonly replayed: boolean;
}

export interface FeePostingBatchCommand {
  readonly policyId: string;
  readonly businessDate: string;
  readonly requestedBy: string;
  readonly actorRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface FeePostingBatchDto {
  readonly batchId: string;
  readonly policyId: string;
  readonly feePolicyVersionId: string;
  readonly businessDate: string;
  readonly idempotencyKey: string;
  readonly status: string;
  readonly ledgerTransactionId: string;
  readonly totalFeeMinor: number;
  readonly accountCount: number;
  readonly requestedBy: string;
  readonly reason: string;
  readonly postedAt?: string | null;
}

export interface FeePostingBatchResponse {
  readonly item: FeePostingBatchDto;
  readonly ledgerTransaction?: LedgerTransactionDto | null;
  readonly replayed: boolean;
}

export interface EodCloseCommand {
  readonly businessDate: string;
  readonly idempotencyKey?: string | null;
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string | null;
  readonly feePolicyId?: string | null;
  readonly externalMode?: string | null;
}

export interface EodClosingStepDto {
  readonly businessDate: string;
  readonly step: "INTEREST_ACCRUAL" | "INTEREST_POSTING" | "FEE_POSTING" | "RECONCILIATION" | "DAILY_CLOSING";
  readonly status: string;
  readonly startedAt?: string | null;
  readonly finishedAt?: string | null;
  readonly result: Record<string, unknown>;
}

export interface EodClosingMonitorDto {
  readonly businessDate: string;
  readonly status: string;
  readonly dailyClosingStatus?: string | null;
  readonly ledgerTotalHash?: string | null;
  readonly steps: readonly EodClosingStepDto[];
  readonly reconciliationItems: readonly ReconciliationItemDto[];
  readonly syntheticOnly: boolean;
}

export interface EodCloseRequestResponse {
  readonly approval?: OperatorApproval | null;
  readonly monitor: EodClosingMonitorDto;
  readonly replayed: boolean;
}

export interface LoanProductDto {
  readonly productId: string;
  readonly productCode: string;
  readonly productName: string;
  readonly currency: string;
  readonly annualRateBps: number;
  readonly termMonths: number;
  readonly minimumAmountMinor: number;
  readonly maximumAmountMinor: number;
  readonly approvalThresholdMinor: number;
  readonly status: string;
  readonly syntheticOnly: boolean;
}

export interface LoanProductListResponse {
  readonly items: readonly LoanProductDto[];
}

export interface LoanApplicationCommand {
  readonly customerId: string;
  readonly depositAccountId: string;
  readonly productId: string;
  readonly requestedAmountMinor: number;
  readonly requestedTermMonths?: number | null;
  readonly syntheticMonthlyIncomeMinor: number;
  readonly syntheticMonthlyDebtMinor: number;
  readonly syntheticCreditGrade: string;
  readonly syntheticRiskGrade: string;
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface LoanApplicationDto {
  readonly applicationId: string;
  readonly customerId: string;
  readonly depositAccountId: string;
  readonly productId: string;
  readonly requestedAmountMinor: number;
  readonly requestedTermMonths: number;
  readonly syntheticCreditGrade: string;
  readonly syntheticRiskGrade: string;
  readonly underwritingScore: number;
  readonly underwritingDecision: string;
  readonly status: string;
  readonly approvalId?: string | null;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly executedAt?: string | null;
  readonly syntheticOnly: boolean;
}

export interface LoanApplicationResponse {
  readonly item: LoanApplicationDto;
  readonly approval?: OperatorApproval | null;
  readonly replayed: boolean;
}

export interface LoanScheduleItemDto {
  readonly scheduleId: string;
  readonly installmentNo: number;
  readonly dueDate: string;
  readonly principalMinor: number;
  readonly interestMinor: number;
  readonly totalMinor: number;
  readonly status: string;
  readonly ledgerTransactionId?: string | null;
  readonly paidAt?: string | null;
}

export interface LoanDto {
  readonly loanId: string;
  readonly applicationId: string;
  readonly customerId: string;
  readonly depositAccountId: string;
  readonly productId: string;
  readonly principalMinor: number;
  readonly outstandingPrincipalMinor: number;
  readonly annualRateBps: number;
  readonly termMonths: number;
  readonly status: string;
  readonly disbursementTransactionId?: string | null;
  readonly nextDueDate?: string | null;
  readonly overdueDays: number;
  readonly disbursedAt?: string | null;
  readonly schedule: readonly LoanScheduleItemDto[];
  readonly syntheticOnly: boolean;
}

export interface LoanExecutionResponse {
  readonly application: LoanApplicationDto;
  readonly loan: LoanDto;
  readonly ledgerTransaction: LedgerCommandResult;
}

export interface LoanPaymentCommand {
  readonly principalMinor?: number | null;
  readonly interestMinor?: number | null;
  readonly businessDate?: string | null;
  readonly idempotencyKey?: string | null;
  readonly requestedBy?: string | null;
  readonly requestedChannel?: string | null;
  readonly reason?: string | null;
}

export interface LoanPaymentDto {
  readonly paymentId: string;
  readonly loanId: string;
  readonly paymentType: string;
  readonly principalMinor: number;
  readonly interestMinor: number;
  readonly totalMinor: number;
  readonly businessDate: string;
  readonly idempotencyKey: string;
  readonly ledgerTransactionId: string;
  readonly requestedBy: string;
  readonly requestedChannel: string;
  readonly reason: string;
  readonly createdAt: string;
}

export interface LoanPaymentResponse {
  readonly item: LoanPaymentDto;
  readonly loan: LoanDto;
  readonly ledgerTransaction: LedgerCommandResult;
  readonly replayed: boolean;
}

export interface LoanAccrualCommand {
  readonly accrualDate: string;
  readonly requestedBy: string;
  readonly actorRole: string;
  readonly reason: string;
}

export interface LoanAccrualDto {
  readonly accrualId: string;
  readonly loanId: string;
  readonly accrualDate: string;
  readonly outstandingPrincipalMinor: number;
  readonly annualRateBps: number;
  readonly interestMinor: number;
  readonly overdueDays: number;
  readonly status: string;
  readonly createdAt: string;
}

export interface LoanAccrualResponse {
  readonly item: LoanAccrualDto;
  readonly loan: LoanDto;
  readonly replayed: boolean;
}

export interface IssueCardCommand {
  readonly customerId: string;
  readonly accountId: string;
  readonly panToken: string;
  readonly panLast4: string;
  readonly dailyLimitMinor: number;
  readonly monthlyLimitMinor: number;
  readonly singleLimitMinor: number;
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface CardDto {
  readonly cardId: string;
  readonly customerId: string;
  readonly accountId: string;
  readonly panToken: string;
  readonly panLast4: string;
  readonly status: string;
  readonly dailyLimitMinor: number;
  readonly monthlyLimitMinor: number;
  readonly singleLimitMinor: number;
  readonly createdAt: string;
  readonly syntheticOnly: boolean;
}

export interface CardIssueResponse {
  readonly item: CardDto;
  readonly replayed: boolean;
}

export interface ThreeDsSimulationCommand {
  readonly cardId: string;
  readonly amountMinor: number;
  readonly idempotencyKey: string;
}

export interface ThreeDsSimulationDto {
  readonly authenticationId: string;
  readonly cardId: string;
  readonly amountMinor: number;
  readonly status: string;
  readonly createdAt: string;
  readonly syntheticOnly: boolean;
}

export interface CardAuthorizationCommand {
  readonly cardId: string;
  readonly amountMinor: number;
  readonly merchantName: string;
  readonly businessDate?: string | null;
  readonly threeDsAuthenticationId?: string | null;
  readonly requestedBy?: string | null;
  readonly requestedChannel?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
  readonly currency?: string;
}

export interface CardAuthorizationDto {
  readonly authorizationId: string;
  readonly cardId: string;
  readonly accountId: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly merchantName: string;
  readonly businessDate: string;
  readonly status: string;
  readonly holdId?: string | null;
  readonly threeDsAuthenticationId?: string | null;
  readonly createdAt: string;
}

export interface CardAuthorizationResponse {
  readonly item: CardAuthorizationDto;
  readonly replayed: boolean;
}

export interface CardCaptureCommand {
  readonly businessDate?: string | null;
  readonly requestedBy?: string | null;
  readonly requestedChannel?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface CardCaptureDto {
  readonly captureId: string;
  readonly authorizationId: string;
  readonly cardId: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly ledgerTransactionId: string;
  readonly status: string;
  readonly createdAt: string;
  readonly reversedAt?: string | null;
}

export interface CardCaptureResponse {
  readonly item: CardCaptureDto;
  readonly ledgerTransaction: LedgerCommandResult;
  readonly replayed: boolean;
}

export interface CardCancelCommand {
  readonly requestedBy?: string | null;
  readonly requestedChannel?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface CardLossReportCommand {
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
}

export type PaymentInstructionStatus = "POSTING_REQUESTED" | "SETTLED" | "CANCELED" | "FAILED";
export type PaymentAutopayFrequency = "DAILY" | "WEEKLY" | "MONTHLY";
export type PaymentAutopayStatus = "ACTIVE" | "PAUSED" | "CANCELED";
export type PaymentOutboxDispatchStatus = "PUBLISHED" | "FAILED" | "DEAD_LETTER" | "NO_PENDING_EVENT";
export type PaymentCancellationRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface CreatePaymentInstructionRequest {
  readonly customerId: string;
  readonly debitAccountId: string;
  readonly billerId: string;
  readonly amountMinor: number;
  readonly currency?: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly requestedChannel?: string;
  readonly reason?: string | null;
}

export interface RecordPaymentSettlementRequest {
  readonly ledgerTransactionId: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface CancelPaymentInstructionRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface RequestPaymentCancellationApprovalRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface ReviewPaymentCancellationRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface PaymentInstructionDto {
  readonly paymentInstructionId: string;
  readonly customerId: string;
  readonly debitAccountId: string;
  readonly billerId: string;
  readonly billerName: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly status: PaymentInstructionStatus;
  readonly ledgerTransactionId?: string | null;
  readonly lastOutboxEventId?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface PaymentInstructionResponse {
  readonly item: PaymentInstructionDto;
  readonly replayed: boolean;
  readonly auditEventId?: string | null;
}

export interface PaymentCancellationRequestDto {
  readonly cancellationRequestId: string;
  readonly paymentInstructionId: string;
  readonly status: PaymentCancellationRequestStatus;
  readonly makerId: string;
  readonly makerRole: string;
  readonly makerReason: string;
  readonly checkerId?: string | null;
  readonly checkerRole?: string | null;
  readonly checkerReason?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly decidedAt?: string | null;
}

export interface PaymentCancellationRequestResponse {
  readonly item: PaymentCancellationRequestDto;
  readonly instruction?: PaymentInstructionDto | null;
  readonly replayed: boolean;
}

export interface DispatchPaymentLedgerPostingRequest {
  readonly requestedBy: string;
  readonly reason: string;
  readonly deadLetterThreshold?: number;
}

export interface PaymentOutboxDispatchResponse {
  readonly outboxEventId?: string | null;
  readonly paymentInstructionId?: string | null;
  readonly ledgerTransactionId?: string | null;
  readonly status: PaymentOutboxDispatchStatus;
  readonly retryCount: number;
  readonly syntheticOnly: boolean;
}

export interface CreateAutopayAgreementRequest {
  readonly customerId: string;
  readonly debitAccountId: string;
  readonly billerId: string;
  readonly amountMinor: number;
  readonly currency?: string;
  readonly frequency: PaymentAutopayFrequency;
  readonly nextRunOn: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly requestedChannel?: string;
  readonly reason: string;
}

export interface PauseAutopayAgreementRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface ResumeAutopayAgreementRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
  readonly nextRunOn?: string | null;
}

export interface CancelAutopayAgreementRequest {
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
}

export interface ExecuteDueAutopayRequest {
  readonly businessDate: string;
  readonly idempotencyKey: string;
  readonly requestedBy: string;
  readonly reason: string;
  readonly limit?: number;
}

export interface PaymentAutopayAgreementDto {
  readonly autopayAgreementId: string;
  readonly customerId: string;
  readonly debitAccountId: string;
  readonly billerId: string;
  readonly billerName: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly frequency: PaymentAutopayFrequency;
  readonly status: PaymentAutopayStatus;
  readonly nextRunOn: string;
  readonly lastRunOn?: string | null;
  readonly lastPaymentInstructionId?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface PaymentAutopayAgreementResponse {
  readonly item: PaymentAutopayAgreementDto;
  readonly replayed: boolean;
}

export interface PaymentAutopayExecutionDto {
  readonly autopayExecutionId: string;
  readonly autopayAgreementId: string;
  readonly scheduledRunOn: string;
  readonly paymentInstructionId: string;
  readonly status: "INSTRUCTION_CREATED";
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
}

export interface ExecuteDueAutopayResponse {
  readonly items: readonly PaymentAutopayExecutionDto[];
  readonly executedCount: number;
  readonly replayed: boolean;
}

export type NotificationDeliveryStatus = "PENDING" | "DELIVERED" | "FAILED" | "DEAD_LETTER";
export type NotificationChannel = "SMS" | "EMAIL" | "PUSH" | "CHAT";
export type NotificationProviderKind =
  | "SYNTHETIC_SMS_SINK"
  | "SYNTHETIC_EMAIL_SINK"
  | "SYNTHETIC_PUSH_SINK"
  | "SYNTHETIC_CHAT_SINK";
export type NotificationTemplateStatus = "ACTIVE" | "RETIRED";
export type NotificationTemplateChangeStatus = "PENDING" | "APPROVED" | "REJECTED";
export type NotificationWorkflowStatus = "PENDING_REVIEW" | "APPROVED" | "REJECTED";

export interface ConsumeNotificationEventRequest {
  readonly sourceEventId: string;
  readonly eventType: string;
  readonly recipientId: string;
  readonly channel?: NotificationChannel;
  readonly payload: Record<string, unknown>;
  readonly requestedBy?: string;
}

export interface RecordNotificationFailureRequest {
  readonly errorMessage: string;
  readonly requestedBy: string;
  readonly reason: string;
  readonly deadLetterThreshold?: number;
}

export interface MarkNotificationDeliveredRequest {
  readonly requestedBy: string;
  readonly reason: string;
}

export interface CreateNotificationTemplateChangeRequest {
  readonly eventType: string;
  readonly channel: NotificationChannel;
  readonly version: number;
  readonly bodyTemplate: string;
  readonly providerKind: NotificationProviderKind;
  readonly requestedBy: string;
  readonly reason: string;
  readonly syntheticOnly?: boolean;
}

export interface ApproveNotificationTemplateChangeRequest {
  readonly approvedBy: string;
  readonly approvedByRole: string;
  readonly reason: string;
}

export interface RejectNotificationTemplateChangeRequest {
  readonly rejectedBy: string;
  readonly rejectedByRole: string;
  readonly reason: string;
}

export interface UpsertNotificationPreferenceRequest {
  readonly recipientId: string;
  readonly channel: NotificationChannel;
  readonly eventType?: string | null;
  readonly enabled: boolean;
  readonly requestedBy: string;
  readonly reason: string;
  readonly syntheticOnly?: boolean;
}

export interface UpsertCustomerNotificationPreferenceRequest {
  readonly channel: NotificationChannel;
  readonly eventType?: string | null;
  readonly enabled: boolean;
  readonly syntheticOnly?: boolean;
}

export interface NotificationDeliveryDto {
  readonly deliveryRequestId: string;
  readonly sourceEventId: string;
  readonly eventType: string;
  readonly recipientId: string;
  readonly channel: NotificationChannel;
  readonly providerKind: NotificationProviderKind;
  readonly status: NotificationDeliveryStatus;
  readonly maskedMessage: string;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface NotificationDeliveryResponse {
  readonly items: readonly NotificationDeliveryDto[];
  readonly replayed: boolean;
}

export interface NotificationTemplateDto {
  readonly templateId: string;
  readonly eventType: string;
  readonly channel: NotificationChannel;
  readonly version: number;
  readonly status: NotificationTemplateStatus;
  readonly bodyTemplate: string;
  readonly providerKind: NotificationProviderKind;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
}

export interface NotificationTemplateChangeRequestDto {
  readonly changeRequestId: string;
  readonly eventType: string;
  readonly channel: NotificationChannel;
  readonly requestedVersion: number;
  readonly bodyTemplate: string;
  readonly providerKind: NotificationProviderKind;
  readonly status: NotificationTemplateChangeStatus;
  readonly requestedBy: string;
  readonly requestReason: string;
  readonly requestedAt: string;
  readonly reviewedBy?: string | null;
  readonly reviewedByRole?: string | null;
  readonly reviewedAt?: string | null;
  readonly reviewReason?: string | null;
  readonly approvedTemplateId?: string | null;
  readonly workflowInstanceId: string;
  readonly workflowStatus: NotificationWorkflowStatus;
  readonly workflowTimeline: readonly NotificationWorkflowTimelineEntryDto[];
  readonly syntheticOnly: boolean;
}

export interface NotificationWorkflowTimelineEntryDto {
  readonly workflowEventId: string;
  readonly eventType: string;
  readonly fromStatus?: NotificationWorkflowStatus | null;
  readonly toStatus: NotificationWorkflowStatus;
  readonly actorId: string;
  readonly reason: string;
  readonly occurredAt: string;
  readonly syntheticOnly: boolean;
}

export interface NotificationPreferenceDto {
  readonly preferenceId: string;
  readonly recipientId: string;
  readonly channel: NotificationChannel;
  readonly eventType: string;
  readonly enabled: boolean;
  readonly requestedBy: string;
  readonly reason: string;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface ReportDefinitionDto {
  readonly reportType: string;
  readonly title: string;
  readonly category: string;
  readonly defaultMaskingPolicy: string;
  readonly sensitive: boolean;
  readonly sourceSystems: readonly string[];
  readonly syntheticOnly: boolean;
}

export interface ReportCatalogResponse {
  readonly auditEventId: string;
  readonly items: readonly ReportDefinitionDto[];
  readonly syntheticOnly: boolean;
}

export interface GenerateReportArtifactRequest {
  readonly reportType: string;
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly idempotencyKey?: string;
}

export interface RunReportRetentionSweepRequest {
  readonly requestedBy?: string;
  readonly requestedByRole?: string;
  readonly reason?: string;
  readonly sweepDate?: string;
}

export interface ReportArtifactDto {
  readonly artifactId: string;
  readonly reportType: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly status: string;
  readonly artifactPath: string;
  readonly artifactContent: Record<string, unknown>;
  readonly contentSha256: string;
  readonly retentionPolicy: string;
  readonly retentionUntil?: string | null;
  readonly exportFormat: string;
  readonly sourceReferences: readonly string[];
  readonly maskedByDefault: boolean;
  readonly syntheticOnly: boolean;
  readonly generatedAt?: string | null;
  readonly workflowInstanceId: string;
  readonly workflowStatus: ReportingWorkflowStatus;
  readonly workflowTimeline: readonly ReportingWorkflowTimelineEntryDto[];
}

export type ReportingWorkflowStatus = "GENERATED" | "EXPORTED" | "EXPIRED";

export interface ReportingWorkflowTimelineEntryDto {
  readonly workflowEventId: string;
  readonly eventType: string;
  readonly fromStatus?: ReportingWorkflowStatus | null;
  readonly toStatus: ReportingWorkflowStatus;
  readonly actorId: string;
  readonly actorRole: string;
  readonly reason: string;
  readonly occurredAt: string;
  readonly syntheticOnly: boolean;
}

export interface GenerateReportArtifactResponse {
  readonly item: ReportArtifactDto;
  readonly replayed: boolean;
}

export interface ReportArtifactListResponse {
  readonly auditEventId: string;
  readonly items: readonly ReportArtifactDto[];
  readonly syntheticOnly: boolean;
}

export interface ReportArtifactExportResponse {
  readonly auditEventId: string;
  readonly packageName: string;
  readonly contentSha256: string;
  readonly exportFormat: string;
  readonly item: ReportArtifactDto;
  readonly packageContent: Record<string, unknown>;
  readonly syntheticOnly: boolean;
}

export interface ReportRetentionSweepResponse {
  readonly auditEventId: string;
  readonly sweepDate: string;
  readonly expiredCount: number;
  readonly expiredArtifactIds: readonly string[];
  readonly ledgerRowsMutated: boolean;
  readonly syntheticOnly: boolean;
}

export interface ParameterVersionDto {
  readonly namespace: string;
  readonly parameterVersionId: string;
  readonly parameterKey: string;
  readonly parameterValue: string;
  readonly valueType: string;
  readonly effectiveFrom: string;
  readonly effectiveTo?: string | null;
  readonly approvalId?: string | null;
  readonly createdBy: string;
  readonly approvedAt?: string | null;
  readonly rollbackOfVersionId?: string | null;
  readonly createdAt: string;
  readonly syntheticOnly: boolean;
}

export interface ParameterValueDto {
  readonly namespace: string;
  readonly parameterKey: string;
  readonly currentValue: string;
  readonly currentVersionId: string;
  readonly valueType: string;
  readonly effectiveFrom: string;
  readonly scheduled: readonly ParameterVersionDto[];
  readonly syntheticOnly: boolean;
}

export interface ParameterListResponse {
  readonly auditEventId: string;
  readonly items: readonly ParameterValueDto[];
}

export interface ParameterHistoryResponse {
  readonly auditEventId: string;
  readonly items: readonly ParameterVersionDto[];
}

export interface ParameterChangeRequestCommand {
  readonly parameterKey: string;
  readonly scheduledValue?: unknown;
  readonly effectiveFrom?: string | null;
  readonly effectiveAt?: string | null;
  readonly rollbackOfVersionId?: string | null;
  readonly rollbackPlan: string;
  readonly requestedBy?: string | null;
  readonly requestedByRole?: string | null;
  readonly reason?: string | null;
  readonly idempotencyKey?: string | null;
}

export interface ParameterChangeRequestDto {
  readonly requestId: string;
  readonly namespace: string;
  readonly parameterKey: string;
  readonly businessType: string;
  readonly approvalId: string;
  readonly requestedValue: string;
  readonly valueType: string;
  readonly effectiveFrom: string;
  readonly rollbackPlan: string;
  readonly rollbackOfVersionId?: string | null;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly status: string;
  readonly idempotencyKey: string;
  readonly appliedVersionId?: string | null;
  readonly appliedAt?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly syntheticOnly: boolean;
}

export interface ParameterChangeRequestResponse {
  readonly item: ParameterChangeRequestDto;
  readonly approval?: OperatorApproval | null;
  readonly replayed: boolean;
}

export interface LedgerProjectionDriftRunCommand {
  readonly requestedBy: string;
  readonly requestedByRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
  readonly accountId?: string | null;
  readonly currency?: string | null;
  readonly asOfBusinessDate?: string | null;
}

export interface LedgerProjectionDriftRunDto {
  readonly runId: string;
  readonly status: string;
  readonly requestedBy: string;
  readonly requestedByRole: string;
  readonly reason: string;
  readonly accountId?: string | null;
  readonly currency?: string | null;
  readonly asOfBusinessDate?: string | null;
  readonly sourcePostingCount: number;
  readonly sourceLastPostingId?: string | null;
  readonly sourceHash: string;
  readonly driftItemCount: number;
  readonly auditEventId?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly completedAt?: string | null;
}

export interface LedgerProjectionDriftItemDto {
  readonly itemId: string;
  readonly runId: string;
  readonly accountId: string;
  readonly currency: string;
  readonly expectedLedgerBalanceMinor: number;
  readonly actualLedgerBalanceMinor?: number | null;
  readonly expectedAvailableBalanceMinor: number;
  readonly actualAvailableBalanceMinor?: number | null;
  readonly holdAmountMinor: number;
  readonly driftAmountMinor: number;
  readonly sourcePostingCount: number;
  readonly sourceLastPostingId?: string | null;
  readonly sourceHash: string;
  readonly status: string;
  readonly createdAt: string;
}

export interface LedgerProjectionDriftRunResponse {
  readonly item: LedgerProjectionDriftRunDto;
  readonly items: readonly LedgerProjectionDriftItemDto[];
  readonly replayed: boolean;
}

export interface LedgerProjectionRebuildRequestCommand {
  readonly requestedBy: string;
  readonly requestedByRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
  readonly accountId?: string | null;
  readonly currency?: string | null;
  readonly driftRunId?: string | null;
}

export interface LedgerProjectionRebuildApproveCommand {
  readonly approvedBy: string;
  readonly approvedByRole?: string;
  readonly screenId?: string;
}

export interface LedgerProjectionRebuildRejectCommand {
  readonly rejectedBy: string;
  readonly rejectedByRole?: string;
  readonly rejectReason: string;
  readonly screenId?: string;
}

export interface LedgerProjectionRebuildExecuteCommand {
  readonly executedBy: string;
  readonly executedByRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
}

export interface LedgerProjectionRebuildRequestDto {
  readonly requestId: string;
  readonly driftRunId?: string | null;
  readonly accountId?: string | null;
  readonly currency?: string | null;
  readonly status: string;
  readonly requestedBy: string;
  readonly requestedByRole: string;
  readonly reason: string;
  readonly approvalId: string;
  readonly approvedBy?: string | null;
  readonly approvedAt?: string | null;
  readonly rejectedBy?: string | null;
  readonly rejectedAt?: string | null;
  readonly rejectReason?: string | null;
  readonly beforeSourcePostingCount: number;
  readonly beforeSourceLastPostingId?: string | null;
  readonly beforeSourceHash: string;
  readonly beforeProjectionHash: string;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface LedgerProjectionRebuildRunDto {
  readonly runId: string;
  readonly requestId: string;
  readonly approvalId: string;
  readonly status: string;
  readonly executedBy: string;
  readonly executedByRole: string;
  readonly reason: string;
  readonly accountId?: string | null;
  readonly currency?: string | null;
  readonly beforeSourcePostingCount: number;
  readonly beforeSourceLastPostingId?: string | null;
  readonly beforeSourceHash: string;
  readonly beforeProjectionHash: string;
  readonly afterSourcePostingCount: number;
  readonly afterSourceLastPostingId?: string | null;
  readonly afterSourceHash: string;
  readonly afterProjectionHash: string;
  readonly rebuiltItemCount: number;
  readonly auditEventId?: string | null;
  readonly syntheticOnly: boolean;
  readonly createdAt: string;
  readonly completedAt?: string | null;
}

export interface LedgerProjectionRebuildItemDto {
  readonly itemId: string;
  readonly runId: string;
  readonly accountId: string;
  readonly currency: string;
  readonly previousLedgerBalanceMinor?: number | null;
  readonly rebuiltLedgerBalanceMinor: number;
  readonly previousAvailableBalanceMinor?: number | null;
  readonly rebuiltAvailableBalanceMinor: number;
  readonly holdAmountMinor: number;
  readonly driftAmountMinor: number;
  readonly sourcePostingCount: number;
  readonly sourceLastPostingId?: string | null;
  readonly sourceHash: string;
  readonly status: string;
  readonly createdAt: string;
}

export interface LedgerProjectionRebuildRequestResponse {
  readonly item: LedgerProjectionRebuildRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
}

export interface LedgerProjectionRebuildReviewResponse {
  readonly item: LedgerProjectionRebuildRequestDto;
  readonly approval: OperatorApproval;
  readonly replayed: boolean;
}

export interface LedgerProjectionRebuildRunResponse {
  readonly item: LedgerProjectionRebuildRunDto;
  readonly items: readonly LedgerProjectionRebuildItemDto[];
  readonly replayed: boolean;
}

export interface FdsAnalyticsEvidenceResultDto {
  readonly transactionId: string;
  readonly customerId: string;
  readonly riskBand: string;
  readonly totalScore: number;
  readonly alerts: readonly string[];
  readonly syntheticOnly: boolean;
}

export interface FdsAnalyticsEvidenceDto {
  readonly engine: string;
  readonly generatedAt: string;
  readonly controls: Record<string, boolean>;
  readonly scoredTransactions: number;
  readonly highRiskResults: number;
  readonly alertCounts: Record<string, number>;
  readonly highestRisk?: FdsAnalyticsEvidenceResultDto | null;
  readonly auditEventId: string;
  readonly syntheticOnly: boolean;
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
  readonly feePolicyChangeRequest?: FeePolicyChangeRequestDto | null;
  readonly complaint?: ComplaintCaseDto | null;
  readonly fdsCase?: FdsCaseDto | null;
  readonly amlCase?: AmlCaseDto | null;
  readonly reconciliationItem?: ReconciliationItemDto | null;
  readonly eodClosing?: EodClosingMonitorDto | null;
  readonly loanExecution?: LoanExecutionResponse | null;
  readonly callCenterEscalation?: CallCenterEscalationDto | null;
  readonly ledgerTransaction?: LedgerCommandResult | null;
}

export interface StaffApprovalRejectionResponse {
  readonly item: OperatorApproval;
  readonly rejected: boolean;
  readonly feeWaiverRequest?: FeeWaiverRequestDto | null;
  readonly transactionCorrectionRequest?: TransactionCorrectionRequestDto | null;
  readonly loanApplication?: LoanApplicationDto | null;
  readonly depositRateChangeRequest?: DepositRateChangeRequestDto | null;
  readonly feePolicyChangeRequest?: FeePolicyChangeRequestDto | null;
  readonly callCenterEscalation?: CallCenterEscalationDto | null;
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
  readonly mismatchType: string;
  readonly amountMinor: number;
  readonly internalAmountMinor?: number | null;
  readonly externalAmountMinor?: number | null;
  readonly externalStatus?: string | null;
  readonly feedFileId?: string | null;
  readonly detectedReason: string;
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

export interface AuditExportRequestCommand {
  readonly requestedBy: string;
  readonly requestedRole?: string;
  readonly reason: string;
  readonly idempotencyKey: string;
  readonly exportFormat?: "NDJSON";
}

export interface AuditExportApproveCommand {
  readonly approvedBy: string;
  readonly approvedByRole?: string;
  readonly reason: string;
}

export interface AuditExportRejectCommand {
  readonly rejectedBy: string;
  readonly rejectedByRole?: string;
  readonly reason: string;
}

export interface AuditExportJobResponse {
  readonly item: AuditExportJobDto;
  readonly replayed: boolean;
}

export interface AuditExportJobDto {
  readonly exportId: string;
  readonly status: string;
  readonly requestedBy: string;
  readonly requestedRole: string;
  readonly reason: string;
  readonly exportFormat: string;
  readonly approvalId: string;
  readonly requestedAt: string;
  readonly approvedBy?: string | null;
  readonly approvedRole?: string | null;
  readonly approvedAt?: string | null;
  readonly rejectedBy?: string | null;
  readonly rejectedRole?: string | null;
  readonly rejectedAt?: string | null;
  readonly rejectReason?: string | null;
  readonly fromAuditEventId?: string | null;
  readonly throughAuditEventId?: string | null;
  readonly rowCount: number;
  readonly hashChainStart?: string | null;
  readonly hashChainEnd?: string | null;
  readonly payloadSha256?: string | null;
  readonly storageUri?: string | null;
  readonly ledgerRowsMutated: boolean;
  readonly syntheticOnly: boolean;
  readonly file?: AuditExportFileDto | null;
}

export interface AuditExportFileDto {
  readonly fileId: string;
  readonly fileName: string;
  readonly format: string;
  readonly rowCount: number;
  readonly sha256: string;
  readonly storageUri: string;
  readonly createdAt: string;
  readonly syntheticOnly: boolean;
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

export interface AdminEvidenceLinkDto {
  readonly evidenceId: string;
  readonly title: string;
  readonly path: string;
  readonly status: string;
  readonly controlArea: string;
}

export interface AdminFeatureCoverageDto {
  readonly featureId: string;
  readonly title: string;
  readonly screenId: string;
  readonly apiContract: string;
  readonly evidencePath: string;
  readonly status: string;
}

export interface AdminEvidenceCoverageResponse {
  readonly auditEventId: string;
  readonly generatedAt: string;
  readonly syntheticOnly: boolean;
  readonly evidenceLinks: readonly AdminEvidenceLinkDto[];
  readonly featureCoverage: readonly AdminFeatureCoverageDto[];
}

export interface AdminServiceStatusDto {
  readonly serviceId: string;
  readonly displayName: string;
  readonly status: string;
  readonly evidence: string;
  readonly syntheticOnly: boolean;
}

export interface AdminBatchStatusDto {
  readonly batchType: string;
  readonly latestReferenceId?: string | null;
  readonly businessDate?: string | null;
  readonly status: string;
  readonly itemCount: number;
  readonly lastUpdatedAt?: string | null;
  readonly evidence: string;
}

export interface AdminMonitoringLinkDto {
  readonly system: string;
  readonly url: string;
  readonly status: string;
  readonly evidence: string;
}

export interface AdminSystemStatusResponse {
  readonly auditEventId: string;
  readonly generatedAt: string;
  readonly syntheticOnly: boolean;
  readonly services: readonly AdminServiceStatusDto[];
  readonly batches: readonly AdminBatchStatusDto[];
  readonly monitoringLinks: readonly AdminMonitoringLinkDto[];
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
  const parameterQuery = (reason: string, asOf?: string): Record<string, string> =>
    asOf ? { reason, asOf } : { reason };
  const getParameters = (path: string, reason: string, asOf?: string) =>
    request<ParameterListResponse>(fetchImpl, baseUrl, path, parameterQuery(reason, asOf), options.bearerToken);
  const getParameterHistory = (path: string, reason: string) =>
    request<ParameterHistoryResponse>(fetchImpl, baseUrl, path, { reason }, options.bearerToken);
  const requestParameterChange = (path: string, command: ParameterChangeRequestCommand) =>
    request<ParameterChangeRequestResponse>(fetchImpl, baseUrl, path, {}, options.bearerToken, { method: "POST", body: command });

  return {
    signupCustomer(command: CustomerSignupCommand) {
      return request<CustomerAuthResponse>(
        fetchImpl,
        baseUrl,
        "/api/auth/customer/signup",
        {},
        undefined,
        { method: "POST", body: command }
      );
    },

    loginCustomer(command: CustomerLoginCommand) {
      return request<CustomerAuthResponse>(
        fetchImpl,
        baseUrl,
        "/api/auth/customer/login",
        {},
        undefined,
        { method: "POST", body: command }
      );
    },

    customerProfile() {
      return request<CustomerProfileDto>(
        fetchImpl,
        baseUrl,
        "/api/customer/me",
        {},
        options.bearerToken
      );
    },

    requestCustomerAccountOpening(command: CustomerSelfServiceAccountOpeningCommand) {
      return request<CustomerSelfServiceAccountOpeningRequestResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/account-opening-requests",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    customerAccountOpeningRequests() {
      return request<CustomerSelfServiceAccountOpeningRequestListResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/account-opening-requests",
        {},
        options.bearerToken
      );
    },

    customer360() {
      return request<Customer360Dto>(
        fetchImpl,
        baseUrl,
        "/api/customer/360",
        {},
        options.bearerToken
      );
    },

    requestStaffCustomerOnboarding(command: CustomerOnboardingRequestCommand) {
      return request<CustomerOnboardingRequestResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/customers/onboarding-requests",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    staffCustomerOnboardingRequest(requestId: string) {
      return request<CustomerOnboardingRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/onboarding-requests/${encodeURIComponent(requestId)}`,
        {},
        options.bearerToken
      );
    },

    approveStaffCustomerOnboardingRequest(requestId: string, command: CustomerOnboardingApproveCommand) {
      return request<CustomerOnboardingReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/onboarding-requests/${encodeURIComponent(requestId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectStaffCustomerOnboardingRequest(requestId: string, command: CustomerOnboardingRejectCommand) {
      return request<CustomerOnboardingReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/onboarding-requests/${encodeURIComponent(requestId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    executeStaffCustomerOnboardingRequest(requestId: string, command: CustomerOnboardingExecuteCommand) {
      return request<CustomerOnboardingExecuteResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/customers/onboarding-requests/${encodeURIComponent(requestId)}/execute`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestStaffAccountOpening(command: AccountOpeningRequestCommand) {
      return request<AccountOpeningRequestResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/accounts/opening-requests",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    staffAccountOpeningRequest(requestId: string) {
      return request<AccountOpeningRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/opening-requests/${encodeURIComponent(requestId)}`,
        {},
        options.bearerToken
      );
    },

    approveStaffAccountOpeningRequest(requestId: string, command: AccountOpeningApproveCommand) {
      return request<AccountOpeningReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/opening-requests/${encodeURIComponent(requestId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectStaffAccountOpeningRequest(requestId: string, command: AccountOpeningRejectCommand) {
      return request<AccountOpeningReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/opening-requests/${encodeURIComponent(requestId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    executeStaffAccountOpeningRequest(requestId: string, command: AccountOpeningExecuteCommand) {
      return request<AccountOpeningExecuteResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/accounts/opening-requests/${encodeURIComponent(requestId)}/execute`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

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

    staffOperationalRetryQueue(reason: string, status?: string) {
      return request<StaffAccessListResponse<OperationalRetryQueueItemDto>>(
        fetchImpl,
        baseUrl,
        "/api/staff/operations/retry-queue",
        status ? { reason, status } : { reason },
        options.bearerToken
      );
    },

    staffWorkflowTimeline(businessReferenceId: string, reason: string) {
      return request<StaffAccessListResponse<StaffWorkflowTimelineEntryDto>>(
        fetchImpl,
        baseUrl,
        `/api/staff/workflows/${encodeURIComponent(businessReferenceId)}/timeline`,
        { reason },
        options.bearerToken
      );
    },

    searchCallCenterCustomers(query: string, reason: string) {
      return request<CallCenterCustomerSearchResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/call-center/customers/search",
        { query, reason },
        options.bearerToken
      );
    },

    startCallCenterInteraction(command: StartCallCenterInteractionCommand) {
      return request<CallCenterInteractionResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/call-center/interactions",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    callCenterInteraction(interactionId: string, reason: string) {
      return request<CallCenterInteractionResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/interactions/${encodeURIComponent(interactionId)}`,
        { reason },
        options.bearerToken
      );
    },

    addCallCenterNote(interactionId: string, command: CallCenterNoteCommand) {
      return request<CallCenterNoteResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/interactions/${encodeURIComponent(interactionId)}/notes`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    createCallCenterAftercallTask(interactionId: string, command: CallCenterAftercallTaskCommand) {
      return request<CallCenterAftercallTaskResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/interactions/${encodeURIComponent(interactionId)}/aftercall-tasks`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    escalateCallCenterInteraction(interactionId: string, command: CallCenterEscalationCommand) {
      return request<CallCenterEscalationResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/interactions/${encodeURIComponent(interactionId)}/escalations`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    closeCallCenterInteraction(interactionId: string, command: CloseCallCenterInteractionCommand) {
      return request<CallCenterInteractionResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/interactions/${encodeURIComponent(interactionId)}/close`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    callCenterCustomerHistory(customerId: string, reason: string) {
      return request<CallCenterInteractionListResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/call-center/customers/${encodeURIComponent(customerId)}/history`,
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

    feePolicies(asOf?: string) {
      return request<FeePolicyListResponse>(
        fetchImpl,
        baseUrl,
        "/api/fees/policies",
        asOf ? { asOf } : {},
        options.bearerToken
      );
    },

    feePolicy(policyId: string, asOf?: string) {
      return request<FeePolicyDto>(
        fetchImpl,
        baseUrl,
        `/api/fees/policies/${encodeURIComponent(policyId)}`,
        asOf ? { asOf } : {},
        options.bearerToken
      );
    },

    staffFees(params: { accountId?: string; reason: string; asOf?: string }) {
      return request<StaffFeePolicyListResponse>(
        fetchImpl,
        baseUrl,
        "/api/staff/fees",
        {
          ...(params.accountId ? { accountId: params.accountId } : {}),
          reason: params.reason,
          ...(params.asOf ? { asOf: params.asOf } : {})
        },
        options.bearerToken
      );
    },

    requestFeePolicyChange(policyId: string, command: FeePolicyChangeRequestCommand) {
      return request<FeePolicyChangeRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/staff/fee-policies/${encodeURIComponent(policyId)}/change-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    postFeeBatch(command: FeePostingBatchCommand) {
      return request<FeePostingBatchResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/fee-posting-batches",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestEodClose(command: EodCloseCommand) {
      return request<EodCloseRequestResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/eod/close",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    eodMonitor(businessDate: string) {
      return request<EodClosingMonitorDto>(
        fetchImpl,
        baseUrl,
        `/api/ops/eod/${encodeURIComponent(businessDate)}`,
        {},
        options.bearerToken
      );
    },

    startLedgerProjectionDriftRun(command: LedgerProjectionDriftRunCommand) {
      return request<LedgerProjectionDriftRunResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/ledger/projection-drift-runs",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    ledgerProjectionDriftRun(runId: string) {
      return request<LedgerProjectionDriftRunResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/ledger/projection-drift-runs/${encodeURIComponent(runId)}`,
        {},
        options.bearerToken
      );
    },

    requestLedgerProjectionRebuild(command: LedgerProjectionRebuildRequestCommand) {
      return request<LedgerProjectionRebuildRequestResponse>(
        fetchImpl,
        baseUrl,
        "/api/ops/ledger/projection-rebuild-requests",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    approveLedgerProjectionRebuildRequest(requestId: string, command: LedgerProjectionRebuildApproveCommand) {
      return request<LedgerProjectionRebuildReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/ledger/projection-rebuild-requests/${encodeURIComponent(requestId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectLedgerProjectionRebuildRequest(requestId: string, command: LedgerProjectionRebuildRejectCommand) {
      return request<LedgerProjectionRebuildReviewResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/ledger/projection-rebuild-requests/${encodeURIComponent(requestId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    executeLedgerProjectionRebuild(requestId: string, command: LedgerProjectionRebuildExecuteCommand) {
      return request<LedgerProjectionRebuildRunResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/ledger/projection-rebuild-requests/${encodeURIComponent(requestId)}/execute`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    ledgerProjectionRebuildRun(runId: string) {
      return request<LedgerProjectionRebuildRunResponse>(
        fetchImpl,
        baseUrl,
        `/api/ops/ledger/projection-rebuild-runs/${encodeURIComponent(runId)}`,
        {},
        options.bearerToken
      );
    },

    loanProducts() {
      return request<LoanProductListResponse>(
        fetchImpl,
        baseUrl,
        "/api/loans/products",
        {},
        options.bearerToken
      );
    },

    requestLoanApplication(command: LoanApplicationCommand) {
      return request<LoanApplicationResponse>(
        fetchImpl,
        baseUrl,
        "/api/loans/applications",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    loanDetail(loanId: string, reason?: string) {
      return request<LoanDto>(
        fetchImpl,
        baseUrl,
        `/api/loans/${encodeURIComponent(loanId)}`,
        reason ? { reason } : {},
        options.bearerToken
      );
    },

    repayLoan(loanId: string, command: LoanPaymentCommand) {
      return request<LoanPaymentResponse>(
        fetchImpl,
        baseUrl,
        `/api/loans/${encodeURIComponent(loanId)}/repayments`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    prepayLoan(loanId: string, command: LoanPaymentCommand) {
      return request<LoanPaymentResponse>(
        fetchImpl,
        baseUrl,
        `/api/loans/${encodeURIComponent(loanId)}/prepayments`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    runLoanAccrual(loanId: string, command: LoanAccrualCommand) {
      return request<LoanAccrualResponse>(
        fetchImpl,
        baseUrl,
        `/api/loans/${encodeURIComponent(loanId)}/accruals/run`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    issueCard(command: IssueCardCommand) {
      return request<CardIssueResponse>(
        fetchImpl,
        baseUrl,
        "/api/cards",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    cardDetail(cardId: string) {
      return request<CardDto>(
        fetchImpl,
        baseUrl,
        `/api/cards/${encodeURIComponent(cardId)}`,
        {},
        options.bearerToken
      );
    },

    simulateCardThreeDs(command: ThreeDsSimulationCommand) {
      return request<ThreeDsSimulationDto>(
        fetchImpl,
        baseUrl,
        "/api/cards/3ds-simulations",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    authorizeCard(command: CardAuthorizationCommand) {
      return request<CardAuthorizationResponse>(
        fetchImpl,
        baseUrl,
        "/api/cards/authorizations",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    captureCardAuthorization(authorizationId: string, command: CardCaptureCommand) {
      return request<CardCaptureResponse>(
        fetchImpl,
        baseUrl,
        `/api/cards/authorizations/${encodeURIComponent(authorizationId)}/captures`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    cancelCardAuthorization(authorizationId: string, command: CardCancelCommand) {
      return request<CardAuthorizationResponse>(
        fetchImpl,
        baseUrl,
        `/api/cards/authorizations/${encodeURIComponent(authorizationId)}/cancel`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reverseCardCapture(captureId: string, command: CardCancelCommand) {
      return request<CardCaptureResponse>(
        fetchImpl,
        baseUrl,
        `/api/cards/captures/${encodeURIComponent(captureId)}/reverse`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reportCardLost(cardId: string, command: CardLossReportCommand) {
      return request<CardDto>(
        fetchImpl,
        baseUrl,
        `/api/cards/${encodeURIComponent(cardId)}/loss-report`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    createPaymentInstruction(command: CreatePaymentInstructionRequest) {
      return request<PaymentInstructionResponse>(
        fetchImpl,
        baseUrl,
        "/api/payments/instructions",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    getPaymentInstruction(instructionId: string, reason?: string) {
      return request<PaymentInstructionResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/instructions/${encodeURIComponent(instructionId)}`,
        reason ? { reason } : {},
        options.bearerToken
      );
    },

    recordPaymentSettlement(instructionId: string, command: RecordPaymentSettlementRequest) {
      return request<PaymentInstructionResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/instructions/${encodeURIComponent(instructionId)}/settlements`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    cancelPaymentInstruction(instructionId: string, command: CancelPaymentInstructionRequest) {
      return request<PaymentInstructionResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/instructions/${encodeURIComponent(instructionId)}/cancel`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    requestPaymentCancellationApproval(instructionId: string, command: RequestPaymentCancellationApprovalRequest) {
      return request<PaymentCancellationRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/instructions/${encodeURIComponent(instructionId)}/cancellation-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    approvePaymentCancellationRequest(requestId: string, command: ReviewPaymentCancellationRequest) {
      return request<PaymentCancellationRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/cancellation-requests/${encodeURIComponent(requestId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectPaymentCancellationRequest(requestId: string, command: ReviewPaymentCancellationRequest) {
      return request<PaymentCancellationRequestResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/cancellation-requests/${encodeURIComponent(requestId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    dispatchNextPaymentLedgerPosting(command: DispatchPaymentLedgerPostingRequest) {
      return request<PaymentOutboxDispatchResponse>(
        fetchImpl,
        baseUrl,
        "/api/payments/outbox/ledger-postings/dispatch-next",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    createAutopayAgreement(command: CreateAutopayAgreementRequest) {
      return request<PaymentAutopayAgreementResponse>(
        fetchImpl,
        baseUrl,
        "/api/payments/autopay/agreements",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    getAutopayAgreement(agreementId: string) {
      return request<PaymentAutopayAgreementResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/autopay/agreements/${encodeURIComponent(agreementId)}`,
        {},
        options.bearerToken
      );
    },

    pauseAutopayAgreement(agreementId: string, command: PauseAutopayAgreementRequest) {
      return request<PaymentAutopayAgreementResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/autopay/agreements/${encodeURIComponent(agreementId)}/pause`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    resumeAutopayAgreement(agreementId: string, command: ResumeAutopayAgreementRequest) {
      return request<PaymentAutopayAgreementResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/autopay/agreements/${encodeURIComponent(agreementId)}/resume`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    cancelAutopayAgreement(agreementId: string, command: CancelAutopayAgreementRequest) {
      return request<PaymentAutopayAgreementResponse>(
        fetchImpl,
        baseUrl,
        `/api/payments/autopay/agreements/${encodeURIComponent(agreementId)}/cancel`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    executeDueAutopay(command: ExecuteDueAutopayRequest) {
      return request<ExecuteDueAutopayResponse>(
        fetchImpl,
        baseUrl,
        "/api/payments/autopay/executions/due",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    consumeNotificationEvent(command: ConsumeNotificationEventRequest) {
      return request<NotificationDeliveryResponse>(
        fetchImpl,
        baseUrl,
        "/api/notifications/events",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    getNotificationDelivery(deliveryRequestId: string) {
      return request<NotificationDeliveryDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/deliveries/${encodeURIComponent(deliveryRequestId)}`,
        {},
        options.bearerToken
      );
    },

    listNotificationDeliveries(filters: {
      readonly requestedBy: string;
      readonly reason: string;
      readonly recipientId?: string;
      readonly sourceEventId?: string;
      readonly eventType?: string;
      readonly channel?: NotificationChannel;
      readonly status?: NotificationDeliveryStatus;
      readonly limit?: number;
    }) {
      return request<readonly NotificationDeliveryDto[]>(
        fetchImpl,
        baseUrl,
        "/api/notifications/deliveries",
        {
          requestedBy: filters.requestedBy,
          reason: filters.reason,
          ...(filters.recipientId ? { recipientId: filters.recipientId } : {}),
          ...(filters.sourceEventId ? { sourceEventId: filters.sourceEventId } : {}),
          ...(filters.eventType ? { eventType: filters.eventType } : {}),
          ...(filters.channel ? { channel: filters.channel } : {}),
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.limit ? { limit: String(filters.limit) } : {})
        },
        options.bearerToken
      );
    },

    recordNotificationFailure(deliveryRequestId: string, command: RecordNotificationFailureRequest) {
      return request<NotificationDeliveryDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/deliveries/${encodeURIComponent(deliveryRequestId)}/failures`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    markNotificationDelivered(deliveryRequestId: string, command: MarkNotificationDeliveredRequest) {
      return request<NotificationDeliveryDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/deliveries/${encodeURIComponent(deliveryRequestId)}/delivered`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    listNotificationTemplates(filters: { readonly eventType?: string; readonly channel?: NotificationChannel } = {}) {
      return request<readonly NotificationTemplateDto[]>(
        fetchImpl,
        baseUrl,
        "/api/notifications/templates",
        {
          ...(filters.eventType ? { eventType: filters.eventType } : {}),
          ...(filters.channel ? { channel: filters.channel } : {})
        },
        options.bearerToken
      );
    },

    listNotificationPreferences(filters: {
      readonly requestedBy: string;
      readonly reason: string;
      readonly recipientId?: string;
      readonly channel?: NotificationChannel;
    }) {
      return request<readonly NotificationPreferenceDto[]>(
        fetchImpl,
        baseUrl,
        "/api/notifications/preferences",
        {
          requestedBy: filters.requestedBy,
          reason: filters.reason,
          ...(filters.recipientId ? { recipientId: filters.recipientId } : {}),
          ...(filters.channel ? { channel: filters.channel } : {})
        },
        options.bearerToken
      );
    },

    upsertNotificationPreference(command: UpsertNotificationPreferenceRequest) {
      return request<NotificationPreferenceDto>(
        fetchImpl,
        baseUrl,
        "/api/notifications/preferences",
        {},
        options.bearerToken,
        { method: "PUT", body: command }
      );
    },

    listCustomerNotificationPreferences(customerId: string, filters: { readonly channel?: NotificationChannel } = {}) {
      return request<readonly NotificationPreferenceDto[]>(
        fetchImpl,
        baseUrl,
        `/api/notifications/customers/${encodeURIComponent(customerId)}/preferences`,
        {
          ...(filters.channel ? { channel: filters.channel } : {})
        },
        options.bearerToken
      );
    },

    listCustomerNotificationDeliveries(customerId: string, filters: {
      readonly sourceEventId?: string;
      readonly eventType?: string;
      readonly channel?: NotificationChannel;
      readonly status?: NotificationDeliveryStatus;
      readonly limit?: number;
    } = {}) {
      return request<readonly NotificationDeliveryDto[]>(
        fetchImpl,
        baseUrl,
        `/api/notifications/customers/${encodeURIComponent(customerId)}/deliveries`,
        {
          ...(filters.sourceEventId ? { sourceEventId: filters.sourceEventId } : {}),
          ...(filters.eventType ? { eventType: filters.eventType } : {}),
          ...(filters.channel ? { channel: filters.channel } : {}),
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.limit ? { limit: String(filters.limit) } : {})
        },
        options.bearerToken
      );
    },

    upsertCustomerNotificationPreference(customerId: string, command: UpsertCustomerNotificationPreferenceRequest) {
      return request<NotificationPreferenceDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/customers/${encodeURIComponent(customerId)}/preferences`,
        {},
        options.bearerToken,
        { method: "PUT", body: command }
      );
    },

    createNotificationTemplateChangeRequest(command: CreateNotificationTemplateChangeRequest) {
      return request<NotificationTemplateChangeRequestDto>(
        fetchImpl,
        baseUrl,
        "/api/notifications/templates/change-requests",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    listNotificationTemplateChangeRequests(filters: {
      readonly status?: NotificationTemplateChangeStatus;
    } = {}) {
      return request<readonly NotificationTemplateChangeRequestDto[]>(
        fetchImpl,
        baseUrl,
        "/api/notifications/templates/change-requests",
        {
          ...(filters.status ? { status: filters.status } : {})
        },
        options.bearerToken
      );
    },

    getNotificationTemplateChangeRequest(changeRequestId: string) {
      return request<NotificationTemplateChangeRequestDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/templates/change-requests/${encodeURIComponent(changeRequestId)}`,
        {},
        options.bearerToken
      );
    },

    approveNotificationTemplateChangeRequest(
      changeRequestId: string,
      command: ApproveNotificationTemplateChangeRequest
    ) {
      return request<NotificationTemplateChangeRequestDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/templates/change-requests/${encodeURIComponent(changeRequestId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectNotificationTemplateChangeRequest(
      changeRequestId: string,
      command: RejectNotificationTemplateChangeRequest
    ) {
      return request<NotificationTemplateChangeRequestDto>(
        fetchImpl,
        baseUrl,
        `/api/notifications/templates/change-requests/${encodeURIComponent(changeRequestId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reportCatalog(reason: string) {
      return request<ReportCatalogResponse>(
        fetchImpl,
        baseUrl,
        "/api/reports/catalog",
        { reason },
        options.bearerToken
      );
    },

    generateReportArtifact(command: GenerateReportArtifactRequest) {
      return request<GenerateReportArtifactResponse>(
        fetchImpl,
        baseUrl,
        "/api/reports/artifacts",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reportArtifacts(filters: { readonly reason: string; readonly reportType?: string }) {
      return request<ReportArtifactListResponse>(
        fetchImpl,
        baseUrl,
        "/api/reports/artifacts",
        {
          reason: filters.reason,
          ...(filters.reportType ? { reportType: filters.reportType } : {})
        },
        options.bearerToken
      );
    },

    exportReportArtifact(artifactId: string, reason: string) {
      return request<ReportArtifactExportResponse>(
        fetchImpl,
        baseUrl,
        `/api/reports/artifacts/${encodeURIComponent(artifactId)}/export`,
        { reason },
        options.bearerToken
      );
    },

    runReportRetentionSweep(command: RunReportRetentionSweepRequest) {
      return request<ReportRetentionSweepResponse>(
        fetchImpl,
        baseUrl,
        "/api/reports/retention/sweeps",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reconciliationParameters(reason: string, asOf?: string) {
      return getParameters("/api/ops/parameters/reconciliation", reason, asOf);
    },

    reconciliationParameterHistory(reason: string) {
      return getParameterHistory("/api/ops/parameters/reconciliation/history", reason);
    },

    requestReconciliationParameterChange(command: ParameterChangeRequestCommand) {
      return requestParameterChange("/api/ops/parameters/reconciliation/change-requests", command);
    },

    auditParameters(reason: string, asOf?: string) {
      return getParameters("/api/staff/audit-parameters", reason, asOf);
    },

    auditParameterHistory(reason: string) {
      return getParameterHistory("/api/staff/audit-parameters/history", reason);
    },

    requestAuditParameterChange(command: ParameterChangeRequestCommand) {
      return requestParameterChange("/api/staff/audit-parameters/change-requests", command);
    },

    fdsParameters(reason: string, asOf?: string) {
      return getParameters("/api/staff/fds-parameters", reason, asOf);
    },

    fdsParameterHistory(reason: string) {
      return getParameterHistory("/api/staff/fds-parameters/history", reason);
    },

    requestFdsParameterChange(command: ParameterChangeRequestCommand) {
      return requestParameterChange("/api/staff/fds-parameters/change-requests", command);
    },

    fdsAnalyticsEvidence(reason: string) {
      return request<FdsAnalyticsEvidenceDto>(
        fetchImpl,
        baseUrl,
        "/api/fds/analytics",
        { reason },
        options.bearerToken
      );
    },

    securityParameters(reason: string, asOf?: string) {
      return getParameters("/api/admin/platform/security-parameters", reason, asOf);
    },

    securityParameterHistory(reason: string) {
      return getParameterHistory("/api/admin/platform/security-parameters/history", reason);
    },

    requestSecurityParameterChange(command: ParameterChangeRequestCommand) {
      return requestParameterChange("/api/admin/platform/security-parameters/change-requests", command);
    },

    authorizationParameters(reason: string, asOf?: string) {
      return getParameters("/api/admin/platform/authorization-parameters", reason, asOf);
    },

    authorizationParameterHistory(reason: string) {
      return getParameterHistory("/api/admin/platform/authorization-parameters/history", reason);
    },

    requestAuthorizationParameterChange(command: ParameterChangeRequestCommand) {
      return requestParameterChange("/api/admin/platform/authorization-parameters/change-requests", command);
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

    customerAccounts(customerId: string) {
      return request<CustomerAccountListResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/accounts",
        { customerId },
        options.bearerToken
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

    internalRecipientLookup(query: string) {
      return request<InternalRecipientLookupResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/recipients/internal-account-lookup",
        { query },
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

    customerStatement(customerId: string, from: string, to: string, reason?: string) {
      return request<CustomerStatementDto>(
        fetchImpl,
        baseUrl,
        `/api/customers/${encodeURIComponent(customerId)}/statements`,
        reason ? { from, to, reason } : { from, to },
        options.bearerToken
      );
    },

    customerConsolidatedStatement(from: string, to: string) {
      return request<CustomerStatementDto>(
        fetchImpl,
        baseUrl,
        "/api/customer/statements/consolidated",
        { from, to },
        options.bearerToken
      );
    },

    customerAccountStatement(accountId: string, from: string, to: string) {
      return request<CustomerStatementDto>(
        fetchImpl,
        baseUrl,
        `/api/customer/accounts/${encodeURIComponent(accountId)}/statement`,
        { from, to },
        options.bearerToken
      );
    },

    customerStatementArtifacts() {
      return request<CustomerStatementArtifactListResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/statements/artifacts",
        {},
        options.bearerToken
      );
    },

    transactionConfirmation(transactionId: string, reason?: string) {
      return request<TransactionConfirmationDto>(
        fetchImpl,
        baseUrl,
        `/api/transactions/${encodeURIComponent(transactionId)}/confirmation`,
        reason ? { reason } : {},
        options.bearerToken
      );
    },

    balanceCertificate(accountId: string, date: string, reason?: string) {
      return request<BalanceCertificateDto>(
        fetchImpl,
        baseUrl,
        `/api/accounts/${encodeURIComponent(accountId)}/balance-certificate`,
        reason ? { date, reason } : { date },
        options.bearerToken
      );
    },

    customerAccessHistory(customerId: string, reason?: string) {
      return request<CustomerAccessHistoryDto>(
        fetchImpl,
        baseUrl,
        `/api/customers/${encodeURIComponent(customerId)}/access-history`,
        reason ? { reason } : {},
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

    submitCustomerComplaintMaterial(caseId: string, command: CustomerComplaintMaterialCommand) {
      return request<CustomerComplaintMaterialResponse>(
        fetchImpl,
        baseUrl,
        `/api/customer/complaints/${encodeURIComponent(caseId)}/materials`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    reopenCustomerComplaint(caseId: string, command: CustomerComplaintReopenCommand) {
      return request<CustomerComplaintReopenResponse>(
        fetchImpl,
        baseUrl,
        `/api/customer/complaints/${encodeURIComponent(caseId)}/reopen-requests`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    complaintTypeGuide() {
      return request<ComplaintTypeGuideResponse>(
        fetchImpl,
        baseUrl,
        "/api/customer/complaint-types",
        {},
        options.bearerToken
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

    requestAuditExport(command: AuditExportRequestCommand) {
      return request<AuditExportJobResponse>(
        fetchImpl,
        baseUrl,
        "/api/audit/exports",
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    auditExport(exportId: string, actorId: string, actorRole: string, reason: string) {
      return request<AuditExportJobResponse>(
        fetchImpl,
        baseUrl,
        `/api/audit/exports/${encodeURIComponent(exportId)}`,
        { actorId, actorRole, reason },
        options.bearerToken
      );
    },

    approveAuditExport(exportId: string, command: AuditExportApproveCommand) {
      return request<AuditExportJobResponse>(
        fetchImpl,
        baseUrl,
        `/api/audit/exports/${encodeURIComponent(exportId)}/approve`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
      );
    },

    rejectAuditExport(exportId: string, command: AuditExportRejectCommand) {
      return request<AuditExportJobResponse>(
        fetchImpl,
        baseUrl,
        `/api/audit/exports/${encodeURIComponent(exportId)}/reject`,
        {},
        options.bearerToken,
        { method: "POST", body: command }
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

    adminEvidenceCoverage(reason: string) {
      return request<AdminEvidenceCoverageResponse>(
        fetchImpl,
        baseUrl,
        "/api/admin/platform/evidence-coverage",
        { reason },
        options.bearerToken
      );
    },

    adminSystemStatus(reason: string) {
      return request<AdminSystemStatusResponse>(
        fetchImpl,
        baseUrl,
        "/api/admin/platform/system-status",
        { reason },
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
  options: { readonly method?: "GET" | "POST" | "PUT"; readonly body?: unknown } = {}
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
