package lab.banking.core.staff

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.aml.AmlCaseDto
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.complaint.ComplaintCaseDto
import lab.banking.core.eod.EodClosingMonitorDto
import lab.banking.core.fds.FdsCaseDto
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.loan.LoanApplicationDto
import lab.banking.core.loan.LoanExecutionResponse
import lab.banking.core.parameters.ParameterChangeRequestDto
import lab.banking.core.product.DepositRateChangeRequestDto
import lab.banking.core.product.FeePolicyChangeRequestDto
import lab.banking.core.reconciliation.ReconciliationItemDto

data class StaffAccessItemResponse<T>(
    val auditEventId: String,
    val item: T
)

data class StaffAccessListResponse<T>(
    val auditEventId: String,
    val items: List<T>
)

data class OperationalRetryQueueItemDto(
    val outboxEventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val status: String,
    val retryCount: Int,
    val nextRetryAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val errorMessage: String?,
    val retryEligible: Boolean
)

data class StaffUnmaskResponse(
    val auditEventId: String,
    val expiresInSeconds: Int,
    val item: StaffCustomerDetailDto
)

data class PiiUnmaskCommand(
    val customerId: String,
    val requestedBy: String? = null,
    val actorRole: String? = null,
    val reason: String? = null,
    val screenId: String? = null
)

data class CustomerInfoChangeCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val afterSnapshot: Map<String, Any?>? = null
)

data class CustomerInfoChangeResponse(
    val item: OperatorApproval,
    val customer: StaffCustomerDetailDto
)

data class AccountHoldRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val description: String? = null,
    val holdAmountMinor: Long? = null,
    val idempotencyKey: String? = null
)

data class AccountHoldReleaseRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val description: String? = null,
    val holdAmountMinor: Long? = null,
    val idempotencyKey: String? = null
)

data class AccountHoldRequestDto(
    val requestId: String,
    val businessType: String,
    val businessReferenceId: String,
    val targetCustomerId: String,
    val targetAccountId: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val reasonCode: String,
    val holdAmountMinor: Long,
    val status: String,
    val approvalId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>
)

data class AccountHoldRequestResponse(
    val item: AccountHoldRequestDto,
    val approval: OperatorApproval,
    val account: StaffAccountDto
)

data class TransferLimitChangeRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val description: String? = null,
    val dailyTransferLimitMinor: Long? = null,
    val singleTransferLimitMinor: Long? = null,
    val idempotencyKey: String? = null
)

data class StaffTransferLimitDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val accountStatus: String,
    val currency: String,
    val dailyTransferLimitMinor: Long,
    val singleTransferLimitMinor: Long,
    val updatedAt: OffsetDateTime
)

data class TransferLimitChangeRequestDto(
    val requestId: String,
    val businessType: String,
    val businessReferenceId: String,
    val targetCustomerId: String,
    val targetAccountId: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val reasonCode: String,
    val currentDailyTransferLimitMinor: Long,
    val currentSingleTransferLimitMinor: Long,
    val requestedDailyTransferLimitMinor: Long,
    val requestedSingleTransferLimitMinor: Long,
    val status: String,
    val approvalId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>
)

data class TransferLimitChangeRequestResponse(
    val item: TransferLimitChangeRequestDto,
    val approval: OperatorApproval,
    val limit: StaffTransferLimitDto
)

data class CustomerKycReviewRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val reviewTrigger: String? = null,
    val description: String? = null,
    val idempotencyKey: String? = null
)

data class StaffKycProfileDto(
    val customerId: String,
    val kycStatus: String,
    val sourceOfFundsCode: String,
    val transactionPurposeCode: String,
    val simulatedProviderReference: String,
    val updatedAt: OffsetDateTime
)

data class CustomerKycReviewRequestDto(
    val requestId: String,
    val businessType: String,
    val businessReferenceId: String,
    val targetCustomerId: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val reasonCode: String,
    val reviewTrigger: String,
    val previousKycStatus: String,
    val requestedKycStatus: String,
    val status: String,
    val approvalId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>
)

data class CustomerKycReviewRequestResponse(
    val item: CustomerKycReviewRequestDto,
    val approval: OperatorApproval,
    val kycProfile: StaffKycProfileDto
)

data class FeeWaiverRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val feeCode: String? = null,
    val waivedAmountMinor: Long? = null,
    val currency: String? = null,
    val targetTransactionId: String? = null,
    val description: String? = null,
    val idempotencyKey: String? = null
)

data class FeeWaiverRequestDto(
    val requestId: String,
    val businessType: String,
    val businessReferenceId: String,
    val targetCustomerId: String,
    val targetAccountId: String,
    val targetTransactionId: String?,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val reasonCode: String,
    val feeCode: String,
    val waivedAmountMinor: Long,
    val currency: String,
    val status: String,
    val approvalId: String?,
    val refundLedgerTransactionId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>
)

data class FeeWaiverRequestResponse(
    val item: FeeWaiverRequestDto,
    val approval: OperatorApproval,
    val account: StaffAccountDto
)

data class TransactionCorrectionRequestCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val correctionType: String? = null,
    val targetAccountId: String? = null,
    val businessDate: LocalDate? = null,
    val description: String? = null,
    val idempotencyKey: String? = null
)

data class TransactionCorrectionRequestDto(
    val requestId: String,
    val businessType: String,
    val businessReferenceId: String,
    val targetCustomerId: String,
    val targetAccountId: String,
    val targetTransactionId: String,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val reasonCode: String,
    val correctionType: String,
    val correctionBusinessDate: LocalDate,
    val status: String,
    val approvalId: String?,
    val ledgerTransactionId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val executedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>
)

data class TransactionCorrectionRequestResponse(
    val item: TransactionCorrectionRequestDto,
    val approval: OperatorApproval,
    val account: StaffAccountDto
)

data class StaffApprovalExecutionResponse(
    val item: OperatorApproval,
    val executed: Boolean,
    val customer: StaffCustomerDetailDto?,
    val account: StaffAccountDto?,
    val accountHoldRequest: AccountHoldRequestDto?,
    val transferLimit: StaffTransferLimitDto?,
    val transferLimitChangeRequest: TransferLimitChangeRequestDto?,
    val kycProfile: StaffKycProfileDto?,
    val kycReviewRequest: CustomerKycReviewRequestDto?,
    val feeWaiverRequest: FeeWaiverRequestDto?,
    val transactionCorrectionRequest: TransactionCorrectionRequestDto?,
    val depositRateChangeRequest: DepositRateChangeRequestDto?,
    val feePolicyChangeRequest: FeePolicyChangeRequestDto?,
    val complaint: ComplaintCaseDto?,
    val fdsCase: FdsCaseDto?,
    val amlCase: AmlCaseDto?,
    val reconciliationItem: ReconciliationItemDto?,
    val eodClosing: EodClosingMonitorDto?,
    val loanExecution: LoanExecutionResponse?,
    val parameterChangeRequest: ParameterChangeRequestDto?,
    val ledgerTransaction: LedgerCommandResult?
)

data class StaffApprovalRejectionResponse(
    val item: OperatorApproval,
    val rejected: Boolean,
    val feeWaiverRequest: FeeWaiverRequestDto?,
    val transactionCorrectionRequest: TransactionCorrectionRequestDto?,
    val loanApplication: LoanApplicationDto?,
    val depositRateChangeRequest: DepositRateChangeRequestDto?,
    val feePolicyChangeRequest: FeePolicyChangeRequestDto?,
    val parameterChangeRequest: ParameterChangeRequestDto?
)

data class StaffCustomerRecord(
    val customerId: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val customerGrade: String,
    val riskGrade: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MaskedCustomerDto(
    val customerId: String,
    val maskedName: String,
    val maskedPhone: String?,
    val maskedAddress: String?,
    val customerGrade: String,
    val riskGrade: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StaffCustomerDetailDto(
    val customerId: String,
    val piiExposure: String,
    val customerGrade: String,
    val riskGrade: String,
    val maskedName: String? = null,
    val maskedPhone: String? = null,
    val maskedAddress: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null
)

data class StaffAccountDto(
    val customerId: String,
    val accountId: String,
    val maskedAccountNo: String,
    val status: String,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val availableBalanceMinor: Long,
    val holdAmountMinor: Long
)

data class StaffTransactionDto(
    val ledgerTransactionId: String,
    val transactionType: String,
    val status: String,
    val businessDate: LocalDate,
    val postedAt: OffsetDateTime?,
    val accountId: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val requestedChannel: String,
    val reason: String?
)
