package lab.banking.core.product

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.ledger.domain.LedgerTransactionDto

data class DepositProductDto(
    val productId: String,
    val productCode: String,
    val productName: String,
    val currency: String,
    val status: String,
    val minimumOpeningBalanceMinor: Long,
    val currentRateVersionId: String?,
    val annualRateBps: Int?,
    val rateEffectiveFrom: LocalDate?,
    val rateEffectiveTo: LocalDate?,
    val syntheticOnly: Boolean
)

data class DepositProductListResponse(
    val items: List<DepositProductDto>
)

data class DepositRateChangeRequestCommand(
    val requestedAnnualRateBps: Int,
    val effectiveFrom: LocalDate,
    val requestedBy: String,
    val actorRole: String = "OPS_MANAGER",
    val reason: String,
    val idempotencyKey: String
)

data class DepositRateChangeRequestDto(
    val requestId: String,
    val productId: String,
    val approvalId: String?,
    val requestedAnnualRateBps: Int,
    val effectiveFrom: LocalDate,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val status: String,
    val idempotencyKey: String,
    val appliedRateVersionId: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val appliedAt: OffsetDateTime?
)

data class DepositRateChangeRequestResponse(
    val item: DepositRateChangeRequestDto,
    val approval: OperatorApproval?,
    val replayed: Boolean
)

data class InterestAccrualRunCommand(
    val accrualDate: LocalDate,
    val requestedBy: String,
    val actorRole: String = "OPS_OPERATOR",
    val reason: String
)

data class InterestAccrualDto(
    val accrualId: String,
    val accountId: String,
    val productId: String,
    val rateVersionId: String,
    val accrualDate: LocalDate,
    val balanceMinor: Long,
    val annualRateBps: Int,
    val accruedInterestMinor: Long,
    val status: String,
    val batchId: String?,
    val ledgerTransactionId: String?
)

data class InterestAccrualRunResponse(
    val accrualDate: LocalDate,
    val items: List<InterestAccrualDto>,
    val totalInterestMinor: Long
)

data class InterestPostingBatchCommand(
    val businessDate: LocalDate,
    val requestedBy: String,
    val actorRole: String = "OPS_OPERATOR",
    val reason: String,
    val idempotencyKey: String
)

data class InterestPostingBatchDto(
    val batchId: String,
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val status: String,
    val ledgerTransactionId: String,
    val totalInterestMinor: Long,
    val accountCount: Int,
    val requestedBy: String,
    val reason: String,
    val postedAt: OffsetDateTime?
)

data class InterestPostingBatchResponse(
    val item: InterestPostingBatchDto,
    val ledgerTransaction: LedgerTransactionDto?,
    val replayed: Boolean
)
