package lab.banking.core.reconciliation

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.ledger.domain.PostingDirection
import lab.banking.core.temporal.TemporalWorkflowReference

data class ReconciliationExternalEntryDto(
    val referenceId: String,
    val businessDate: LocalDate,
    val amountMinor: Long,
    val status: String,
    val provider: String = "OPENBANKING-SIM"
)

data class ReconciliationExternalFileDto(
    val fileId: String,
    val businessDate: LocalDate,
    val entries: List<ReconciliationExternalEntryDto>
)

data class ReconciliationClosingDto(
    val closingId: String,
    val businessDate: LocalDate,
    val status: String,
    val ledgerInvariantValid: Boolean,
    val internalEntryCount: Int,
    val externalEntryCount: Int,
    val unmatchedItemCount: Int,
    val internalTotalMinor: Long,
    val externalTotalMinor: Long
)

data class ReconciliationDailyClosingCommand(
    val businessDate: LocalDate? = null,
    val idempotencyKey: String? = null,
    val requestedBy: String? = null,
    val actorRole: String? = null,
    val externalMode: String? = null
)

data class ReconciliationClosingResponse(
    val item: ReconciliationClosingDto,
    val externalFile: ReconciliationExternalFileDto,
    val reconciliationItems: List<ReconciliationItemDto>,
    val replayed: Boolean
)

data class ReconciliationItemDto(
    val itemId: String,
    val businessDate: LocalDate,
    val sourceSystem: String,
    val internalReferenceId: String?,
    val externalReferenceId: String?,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val owner: String?,
    val approvalId: String?,
    val adjustmentTransactionId: String?,
    val adjustmentRequest: ReconciliationAdjustmentRequestDto?,
    val createdAt: OffsetDateTime
)

data class ReconciliationAdjustmentRequestDto(
    val requestId: String,
    val approvalId: String,
    val accountId: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val reason: String,
    val requestedBy: String,
    val status: String,
    val ledgerTransactionId: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val temporalWorkflow: TemporalWorkflowReference?
)

data class ReconciliationAdjustmentCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val accountId: String? = null,
    val direction: PostingDirection? = null,
    val amountMinor: Long? = null,
    val businessDate: LocalDate? = null,
    val idempotencyKey: String? = null
)

data class ReconciliationAdjustmentRequestResponse(
    val item: ReconciliationItemDto,
    val approval: OperatorApproval
)

data class ReconciliationAdjustmentExecutionResponse(
    val item: ReconciliationItemDto,
    val ledgerTransaction: LedgerCommandResult
)
