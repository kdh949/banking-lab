package lab.banking.core.reconciliation

import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApprovalStore
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.ledger.application.AdjustmentCommand
import lab.banking.core.ledger.domain.PostingDirection
import lab.banking.core.workflow.WorkflowErrors

enum class ReconciliationItemStatus {
    OPEN,
    INVESTIGATING,
    MATCHED,
    ADJUSTMENT_REQUESTED,
    ADJUSTED,
    WAIVED,
    CLOSED
}

data class ReconciliationAdjustmentDraft(
    val accountId: String,
    val direction: PostingDirection,
    val amountMinor: Long,
    val businessDate: LocalDate,
    val idempotencyKey: String,
    val requestedBy: String,
    val reason: String
)

data class ReconciliationItem(
    val itemId: String,
    val businessDate: LocalDate,
    val sourceSystem: String,
    val amountMinor: Long,
    val currency: String = "KRW",
    val status: ReconciliationItemStatus,
    val owner: String?,
    val internalReferenceId: String? = null,
    val externalReferenceId: String? = null,
    val approvalId: String? = null,
    val adjustmentDraft: ReconciliationAdjustmentDraft? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class ReconciliationAdjustmentRequest(
    val item: ReconciliationItem,
    val approval: OperatorApproval
)

data class ReconciliationAdjustmentExecution(
    val reconciliationItem: ReconciliationItem,
    val ledgerCommand: AdjustmentCommand
)

class ReconciliationWorkflow(private val approvalStore: ApprovalStore, private val clock: Clock = Clock.systemUTC()) {
    private var nextItemNumber = 1

    fun createMismatch(
        businessDate: LocalDate,
        owner: String,
        amountMinor: Long,
        sourceSystem: String = "EOD_EXTERNAL_SIM",
        internalReferenceId: String? = null,
        externalReferenceId: String? = null
    ): ReconciliationItem {
        if (amountMinor == 0L) {
            throw WorkflowErrors.validation("amountMinor must be non-zero for reconciliation mismatch")
        }
        val now = now()
        val itemId = "REC-${nextItemNumber.toString().padStart(8, '0')}"
        nextItemNumber += 1
        return ReconciliationItem(
            itemId = itemId,
            businessDate = businessDate,
            sourceSystem = sourceSystem,
            amountMinor = amountMinor,
            status = ReconciliationItemStatus.OPEN,
            owner = owner,
            internalReferenceId = internalReferenceId,
            externalReferenceId = externalReferenceId,
            createdAt = now,
            updatedAt = now
        )
    }

    fun requestAdjustment(
        item: ReconciliationItem,
        requestedBy: String,
        reason: String,
        accountId: String,
        direction: PostingDirection,
        amountMinor: Long,
        businessDate: LocalDate,
        idempotencyKey: String,
        requestedByRole: String = "BRANCH_STAFF"
    ): ReconciliationAdjustmentRequest {
        if (item.status != ReconciliationItemStatus.OPEN && item.status != ReconciliationItemStatus.INVESTIGATING) {
            throw WorkflowErrors.stateViolation("reconciliation item is not open for adjustment: ${item.status}")
        }
        if (amountMinor <= 0) {
            throw WorkflowErrors.validation("amountMinor must be a positive integer minor-unit value")
        }
        val draft = ReconciliationAdjustmentDraft(accountId, direction, amountMinor, businessDate, idempotencyKey, requestedBy, reason)
        val requested = item.copy(
            status = ReconciliationItemStatus.ADJUSTMENT_REQUESTED,
            adjustmentDraft = draft,
            updatedAt = now()
        )
        val approval = approvalStore.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT,
                businessReferenceId = item.itemId,
                requestedBy = requestedBy,
                requestReason = reason,
                requestedByRole = requestedByRole,
                beforeSnapshot = mapOf("status" to item.status.name, "businessDate" to item.businessDate.toString()),
                afterSnapshot = mapOf("status" to ReconciliationItemStatus.ADJUSTED.name, "adjustmentBusinessDate" to businessDate.toString()),
                screenId = "OPS-201"
            )
        )
        return ReconciliationAdjustmentRequest(requested.copy(approvalId = approval.approvalId), approval)
    }

    fun applyApprovedAdjustment(item: ReconciliationItem, approval: OperatorApproval): ReconciliationAdjustmentExecution {
        if (item.status != ReconciliationItemStatus.ADJUSTMENT_REQUESTED) {
            throw WorkflowErrors.stateViolation("reconciliation item is not waiting for adjustment approval: ${item.status}")
        }
        requireApproval(approval, item.itemId, ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT)
        val draft = item.adjustmentDraft ?: throw WorkflowErrors.stateViolation("reconciliation adjustment draft is missing")
        val adjusted = item.copy(status = ReconciliationItemStatus.ADJUSTED, approvalId = null, updatedAt = now())
        val command = AdjustmentCommand(
            accountId = draft.accountId,
            direction = draft.direction,
            amountMinor = draft.amountMinor,
            idempotencyKey = draft.idempotencyKey,
            requestedBy = draft.requestedBy,
            requestedChannel = "OPS_RECONCILIATION",
            businessDate = draft.businessDate,
            reason = draft.reason,
            currency = item.currency,
            businessReferenceId = item.itemId,
            approvalId = approval.approvalId
        )
        return ReconciliationAdjustmentExecution(adjusted, command)
    }

    private fun requireApproval(approval: OperatorApproval, referenceId: String, businessType: String) {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessReferenceId != referenceId || approval.businessType != businessType) {
            throw WorkflowErrors.stateViolation("approval does not match reconciliation workflow action")
        }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
