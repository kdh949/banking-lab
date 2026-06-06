package lab.banking.core.ledger.application

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval

data class LedgerProjectionDriftRunCommand(
    val requestedBy: String,
    val requestedByRole: String = "OPS_OPERATOR",
    val reason: String,
    val idempotencyKey: String,
    val accountId: String? = null,
    val currency: String? = null,
    val asOfBusinessDate: LocalDate? = null
)

data class LedgerProjectionRebuildRequestCommand(
    val requestedBy: String,
    val requestedByRole: String = "OPS_OPERATOR",
    val reason: String,
    val idempotencyKey: String,
    val accountId: String? = null,
    val currency: String? = null,
    val driftRunId: String? = null
)

data class LedgerProjectionRebuildApproveCommand(
    val approvedBy: String,
    val approvedByRole: String = "OPS_MANAGER",
    val screenId: String? = "OPS-LEDGER-102"
)

data class LedgerProjectionRebuildRejectCommand(
    val rejectedBy: String,
    val rejectedByRole: String = "OPS_MANAGER",
    val rejectReason: String,
    val screenId: String? = "OPS-LEDGER-102"
)

data class LedgerProjectionRebuildExecuteCommand(
    val executedBy: String,
    val executedByRole: String = "OPS_OPERATOR",
    val reason: String,
    val idempotencyKey: String
)

data class LedgerProjectionDriftRunDto(
    val runId: String,
    val status: String,
    val requestedBy: String,
    val requestedByRole: String,
    val reason: String,
    val accountId: String?,
    val currency: String?,
    val asOfBusinessDate: LocalDate?,
    val sourcePostingCount: Long,
    val sourceLastPostingId: String?,
    val sourceHash: String,
    val driftItemCount: Int,
    val auditEventId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)

data class LedgerProjectionDriftItemDto(
    val itemId: String,
    val runId: String,
    val accountId: String,
    val currency: String,
    val expectedLedgerBalanceMinor: Long,
    val actualLedgerBalanceMinor: Long?,
    val expectedAvailableBalanceMinor: Long,
    val actualAvailableBalanceMinor: Long?,
    val holdAmountMinor: Long,
    val driftAmountMinor: Long,
    val sourcePostingCount: Long,
    val sourceLastPostingId: String?,
    val sourceHash: String,
    val status: String,
    val createdAt: OffsetDateTime
)

data class LedgerProjectionRebuildRequestDto(
    val requestId: String,
    val driftRunId: String?,
    val accountId: String?,
    val currency: String?,
    val status: String,
    val requestedBy: String,
    val requestedByRole: String,
    val reason: String,
    val approvalId: String,
    val approvedBy: String?,
    val approvedAt: OffsetDateTime?,
    val rejectedBy: String?,
    val rejectedAt: OffsetDateTime?,
    val rejectReason: String?,
    val beforeSourcePostingCount: Long,
    val beforeSourceLastPostingId: String?,
    val beforeSourceHash: String,
    val beforeProjectionHash: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class LedgerProjectionRebuildRunDto(
    val runId: String,
    val requestId: String,
    val approvalId: String,
    val status: String,
    val executedBy: String,
    val executedByRole: String,
    val reason: String,
    val accountId: String?,
    val currency: String?,
    val beforeSourcePostingCount: Long,
    val beforeSourceLastPostingId: String?,
    val beforeSourceHash: String,
    val beforeProjectionHash: String,
    val afterSourcePostingCount: Long,
    val afterSourceLastPostingId: String?,
    val afterSourceHash: String,
    val afterProjectionHash: String,
    val rebuiltItemCount: Int,
    val auditEventId: String?,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?
)

data class LedgerProjectionRebuildItemDto(
    val itemId: String,
    val runId: String,
    val accountId: String,
    val currency: String,
    val previousLedgerBalanceMinor: Long?,
    val rebuiltLedgerBalanceMinor: Long,
    val previousAvailableBalanceMinor: Long?,
    val rebuiltAvailableBalanceMinor: Long,
    val holdAmountMinor: Long,
    val driftAmountMinor: Long,
    val sourcePostingCount: Long,
    val sourceLastPostingId: String?,
    val sourceHash: String,
    val status: String,
    val createdAt: OffsetDateTime
)

data class LedgerProjectionDriftRunResponse(
    val item: LedgerProjectionDriftRunDto,
    val items: List<LedgerProjectionDriftItemDto>,
    val replayed: Boolean
)

data class LedgerProjectionRebuildRequestResponse(
    val item: LedgerProjectionRebuildRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean
)

data class LedgerProjectionRebuildReviewResponse(
    val item: LedgerProjectionRebuildRequestDto,
    val approval: OperatorApproval,
    val replayed: Boolean
)

data class LedgerProjectionRebuildRunResponse(
    val item: LedgerProjectionRebuildRunDto,
    val items: List<LedgerProjectionRebuildItemDto>,
    val replayed: Boolean
)
