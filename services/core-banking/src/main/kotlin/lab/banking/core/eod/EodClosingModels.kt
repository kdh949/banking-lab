package lab.banking.core.eod

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.reconciliation.ReconciliationItemDto

enum class EodClosingStep {
    INTEREST_ACCRUAL,
    INTEREST_POSTING,
    FEE_POSTING,
    RECONCILIATION,
    DAILY_CLOSING
}

data class EodCloseCommand(
    val businessDate: LocalDate,
    val idempotencyKey: String? = null,
    val requestedBy: String = "ops01",
    val requestedByRole: String = "OPS_OPERATOR",
    val reason: String? = null,
    val feePolicyId: String? = null,
    val externalMode: String? = null
)

data class EodCloseRequestResponse(
    val approval: OperatorApproval?,
    val monitor: EodClosingMonitorDto,
    val replayed: Boolean
)

data class EodClosingExecutionResponse(
    val monitor: EodClosingMonitorDto,
    val replayed: Boolean
)

data class EodClosingMonitorDto(
    val businessDate: LocalDate,
    val status: String,
    val dailyClosingStatus: String?,
    val ledgerTotalHash: String?,
    val steps: List<EodClosingStepDto>,
    val reconciliationItems: List<ReconciliationItemDto>,
    val syntheticOnly: Boolean = true
)

data class EodClosingStepDto(
    val businessDate: LocalDate,
    val step: EodClosingStep,
    val status: String,
    val startedAt: OffsetDateTime?,
    val finishedAt: OffsetDateTime?,
    val result: Map<String, Any?>
)
