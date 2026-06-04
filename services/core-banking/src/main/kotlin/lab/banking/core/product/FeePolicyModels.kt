package lab.banking.core.product

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.ledger.domain.LedgerTransactionDto

data class FeePolicyDto(
    val policyId: String,
    val feeCode: String,
    val feeName: String,
    val productId: String?,
    val currency: String,
    val status: String,
    val waiverEligible: Boolean,
    val currentVersionId: String?,
    val amountMinor: Long?,
    val effectiveFrom: LocalDate?,
    val effectiveTo: LocalDate?,
    val syntheticOnly: Boolean
)

data class FeePolicyListResponse(
    val items: List<FeePolicyDto>
)

data class StaffFeePolicyListResponse(
    val auditEventId: String,
    val items: List<FeePolicyDto>
)

data class FeePolicyChangeRequestCommand(
    val requestedAmountMinor: Long,
    val effectiveFrom: LocalDate,
    val requestedBy: String,
    val actorRole: String = "OPS_MANAGER",
    val reason: String,
    val idempotencyKey: String
)

data class FeePolicyChangeRequestDto(
    val requestId: String,
    val policyId: String,
    val approvalId: String?,
    val requestedAmountMinor: Long,
    val effectiveFrom: LocalDate,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val status: String,
    val idempotencyKey: String,
    val appliedFeePolicyVersionId: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val appliedAt: OffsetDateTime?
)

data class FeePolicyChangeRequestResponse(
    val item: FeePolicyChangeRequestDto,
    val approval: OperatorApproval?,
    val replayed: Boolean
)

data class FeePostingBatchCommand(
    val policyId: String,
    val businessDate: LocalDate,
    val requestedBy: String,
    val actorRole: String = "OPS_OPERATOR",
    val reason: String,
    val idempotencyKey: String
)

data class FeePostingBatchDto(
    val batchId: String,
    val policyId: String,
    val feePolicyVersionId: String,
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val status: String,
    val ledgerTransactionId: String,
    val totalFeeMinor: Long,
    val accountCount: Int,
    val requestedBy: String,
    val reason: String,
    val postedAt: OffsetDateTime?
)

data class FeePostingBatchResponse(
    val item: FeePostingBatchDto,
    val ledgerTransaction: LedgerTransactionDto?,
    val replayed: Boolean
)
