package lab.banking.core.fds

import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApprovalServicePort
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.ledger.application.InternalTransferCommand
import lab.banking.core.workflow.WorkflowErrors

enum class FdsCaseStatus {
    HELD,
    INVESTIGATING,
    RELEASE_REQUESTED,
    RELEASED,
    BLOCK_REQUESTED,
    BLOCKED
}

enum class HeldTransferStatus {
    HELD,
    POSTED,
    BLOCKED
}

data class FdsAlert(
    val ruleId: String,
    val message: String
)

data class HeldTransfer(
    val caseId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val idempotencyKey: String,
    val requestedBy: String,
    val customerId: String,
    val businessDate: LocalDate? = null,
    val status: HeldTransferStatus = HeldTransferStatus.HELD
)

data class FdsCase(
    val caseId: String,
    val transfer: HeldTransfer,
    val customerId: String,
    val status: FdsCaseStatus,
    val riskScore: Int,
    val alerts: List<FdsAlert>,
    val owner: String? = null,
    val approvalId: String? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class FdsHoldResult(
    val item: HeldTransfer,
    val fdsCase: FdsCase
)

data class FdsApprovalRequest(
    val item: FdsCase,
    val approval: OperatorApproval
)

data class FdsReleaseExecution(
    val fdsCase: FdsCase,
    val transfer: HeldTransfer,
    val ledgerCommand: InternalTransferCommand
)

data class FdsBlockExecution(
    val fdsCase: FdsCase,
    val transfer: HeldTransfer
)

class FdsWorkflow(private val approvalStore: ApprovalServicePort, private val clock: Clock = Clock.systemUTC()) {
    private var nextCaseNumber = 1

    fun holdTransfer(
        fromAccountId: String,
        toAccountId: String,
        amountMinor: Long,
        idempotencyKey: String,
        requestedBy: String,
        customerId: String,
        newDevice: Boolean = false,
        firstTimeBeneficiary: Boolean = false,
        businessDate: LocalDate? = null
    ): FdsHoldResult {
        if (amountMinor <= 0) {
            throw WorkflowErrors.validation("amountMinor must be a positive integer minor-unit value")
        }
        val alerts = buildAlerts(amountMinor, newDevice, firstTimeBeneficiary)
        val caseId = "FDS-${nextCaseNumber.toString().padStart(8, '0')}"
        nextCaseNumber += 1
        val now = now()
        val transfer = HeldTransfer(caseId, fromAccountId, toAccountId, amountMinor, idempotencyKey, requestedBy, customerId, businessDate)
        val case = FdsCase(
            caseId = caseId,
            transfer = transfer,
            customerId = customerId,
            status = FdsCaseStatus.HELD,
            riskScore = riskScore(alerts),
            alerts = alerts,
            createdAt = now,
            updatedAt = now
        )
        return FdsHoldResult(transfer, case)
    }

    fun assign(case: FdsCase, actorId: String, owner: String): FdsCase {
        requireStatus(case, FdsCaseStatus.HELD)
        return case.copy(status = FdsCaseStatus.INVESTIGATING, owner = owner, updatedAt = now())
    }

    fun requestRelease(case: FdsCase, actorId: String, requestedByRole: String = "FDS_REVIEWER", reason: String): FdsApprovalRequest {
        requireStatus(case, FdsCaseStatus.INVESTIGATING)
        val requested = case.copy(status = FdsCaseStatus.RELEASE_REQUESTED, updatedAt = now())
        val approval = approvalStore.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.FDS_RELEASE,
                businessReferenceId = case.caseId,
                requestedBy = actorId,
                requestReason = reason,
                requestedByRole = requestedByRole,
                beforeSnapshot = mapOf("status" to case.status.name),
                afterSnapshot = mapOf(
                    "status" to FdsCaseStatus.RELEASED.name,
                    "releaseIdempotencyKey" to releaseIdempotencyKey(case.caseId)
                ),
                screenId = "FDS-201"
            )
        )
        return FdsApprovalRequest(requested.copy(approvalId = approval.approvalId), approval)
    }

    fun requestBlock(case: FdsCase, actorId: String, requestedByRole: String = "FDS_REVIEWER", reason: String): FdsApprovalRequest {
        requireStatus(case, FdsCaseStatus.INVESTIGATING)
        val requested = case.copy(status = FdsCaseStatus.BLOCK_REQUESTED, updatedAt = now())
        val approval = approvalStore.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.FDS_BLOCK,
                businessReferenceId = case.caseId,
                requestedBy = actorId,
                requestReason = reason,
                requestedByRole = requestedByRole,
                beforeSnapshot = mapOf("status" to case.status.name),
                afterSnapshot = mapOf("status" to FdsCaseStatus.BLOCKED.name),
                screenId = "FDS-201"
            )
        )
        return FdsApprovalRequest(requested.copy(approvalId = approval.approvalId), approval)
    }

    fun applyApprovedRelease(case: FdsCase, approval: OperatorApproval): FdsReleaseExecution {
        requireStatus(case, FdsCaseStatus.RELEASE_REQUESTED)
        requireApproval(approval, case.caseId, ApprovalBusinessTypes.FDS_RELEASE)
        val postedTransfer = case.transfer.copy(status = HeldTransferStatus.POSTED)
        val releasedCase = case.copy(status = FdsCaseStatus.RELEASED, transfer = postedTransfer, approvalId = null, updatedAt = now())
        val command = InternalTransferCommand(
            fromAccountId = postedTransfer.fromAccountId,
            toAccountId = postedTransfer.toAccountId,
            amountMinor = postedTransfer.amountMinor,
            idempotencyKey = releaseIdempotencyKey(case.caseId),
            requestedBy = postedTransfer.requestedBy,
            requestedChannel = "CUSTOMER_WEB",
            businessDate = postedTransfer.businessDate,
            reason = "Approved FDS release ${case.caseId}",
            businessReferenceId = case.caseId
        )
        return FdsReleaseExecution(releasedCase, postedTransfer, command)
    }

    fun applyApprovedBlock(case: FdsCase, approval: OperatorApproval): FdsBlockExecution {
        requireStatus(case, FdsCaseStatus.BLOCK_REQUESTED)
        requireApproval(approval, case.caseId, ApprovalBusinessTypes.FDS_BLOCK)
        val blockedTransfer = case.transfer.copy(status = HeldTransferStatus.BLOCKED)
        val blockedCase = case.copy(status = FdsCaseStatus.BLOCKED, transfer = blockedTransfer, approvalId = null, updatedAt = now())
        return FdsBlockExecution(blockedCase, blockedTransfer)
    }

    private fun buildAlerts(amountMinor: Long, newDevice: Boolean, firstTimeBeneficiary: Boolean): List<FdsAlert> =
        buildList {
            if (amountMinor >= 5_000_000) {
                add(FdsAlert("FDS-RULE-UNUSUAL-AMOUNT", "Synthetic unusual transfer amount"))
            }
            if (newDevice) {
                add(FdsAlert("FDS-RULE-NEW-DEVICE", "Synthetic new device transfer"))
            }
            if (firstTimeBeneficiary) {
                add(FdsAlert("FDS-RULE-FIRST-BENEFICIARY", "Synthetic first-time beneficiary"))
            }
        }

    private fun riskScore(alerts: List<FdsAlert>): Int = (alerts.size * 300).coerceAtMost(1000)

    private fun releaseIdempotencyKey(caseId: String): String = "FDS-RELEASE-$caseId"

    private fun requireStatus(case: FdsCase, status: FdsCaseStatus) {
        if (case.status != status) {
            throw WorkflowErrors.stateViolation("invalid FDS transition ${case.status} -> $status")
        }
    }

    private fun requireApproval(approval: OperatorApproval, referenceId: String, businessType: String) {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessReferenceId != referenceId || approval.businessType != businessType) {
            throw WorkflowErrors.stateViolation("approval does not match FDS workflow action")
        }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
