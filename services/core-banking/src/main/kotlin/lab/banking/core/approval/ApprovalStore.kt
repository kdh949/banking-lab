package lab.banking.core.approval

import java.time.Clock
import java.time.OffsetDateTime
import java.util.LinkedHashMap
import lab.banking.core.workflow.WorkflowErrors

class ApprovalStore(private val clock: Clock = Clock.systemUTC()) : ApprovalServicePort {
    private val approvals = LinkedHashMap<String, OperatorApproval>()
    private val auditEvents = mutableListOf<ApprovalAuditEvent>()
    private var nextApprovalNumber = 1
    private var nextAuditNumber = 1

    override fun submit(command: SubmitApprovalCommand): OperatorApproval {
        requireNonBlank(command.businessType, "businessType")
        requireNonBlank(command.businessReferenceId, "businessReferenceId")
        requireNonBlank(command.requestedBy, "requestedBy")
        if (ApprovalBusinessTypes.highRisk.contains(command.businessType)) {
            if (command.requestReason.isNullOrBlank()) {
                throw WorkflowErrors.reasonRequired("high-risk approval requires requestReason")
            }
            if (command.afterSnapshot == null) {
                throw WorkflowErrors.validation("high-risk approval requires afterSnapshot")
            }
        }

        val approvalId = "APR-${nextApprovalNumber.toString().padStart(8, '0')}"
        nextApprovalNumber += 1
        val event = appendAudit(
            eventType = "COMMAND_REQUESTED",
            actorId = command.requestedBy,
            actorRole = command.requestedByRole,
            screenId = command.screenId,
            businessReferenceId = command.businessReferenceId,
            reason = command.requestReason,
            payload = mapOf("approvalId" to approvalId, "businessType" to command.businessType)
        )
        val approval = OperatorApproval(
            approvalId = approvalId,
            businessType = command.businessType,
            businessReferenceId = command.businessReferenceId,
            requestedBy = command.requestedBy,
            requestedAt = now(),
            requestReason = command.requestReason.orEmpty(),
            beforeSnapshot = command.beforeSnapshot,
            afterSnapshot = command.afterSnapshot,
            status = ApprovalStatus.PENDING,
            auditEventId = event.auditEventId
        )
        approvals[approvalId] = approval
        return approval
    }

    override fun approve(approvalId: String, command: ApproveApprovalCommand): OperatorApproval {
        requireNonBlank(command.approvedBy, "approvedBy")
        val approval = approval(approvalId)
        if (approval.status != ApprovalStatus.PENDING) {
            throw WorkflowErrors.stateViolation("only pending approvals can be approved")
        }
        if (approval.requestedBy == command.approvedBy) {
            throw WorkflowErrors.selfApprovalRejected()
        }
        val approved = approval.copy(
            status = ApprovalStatus.APPROVED,
            approvedBy = command.approvedBy,
            approvedAt = now()
        )
        approvals[approvalId] = approved
        appendAudit(
            eventType = "COMMAND_APPROVED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId,
            businessReferenceId = approval.businessReferenceId,
            reason = null,
            payload = mapOf("approvalId" to approvalId, "businessType" to approval.businessType)
        )
        return approved
    }

    override fun reject(approvalId: String, command: RejectApprovalCommand): OperatorApproval {
        requireNonBlank(command.rejectedBy, "rejectedBy")
        requireNonBlank(command.rejectReason, "rejectReason")
        val approval = approval(approvalId)
        if (approval.status != ApprovalStatus.PENDING) {
            throw WorkflowErrors.stateViolation("only pending approvals can be rejected")
        }
        val rejected = approval.copy(
            status = ApprovalStatus.REJECTED,
            rejectedBy = command.rejectedBy,
            rejectedAt = now(),
            rejectReason = command.rejectReason
        )
        approvals[approvalId] = rejected
        appendAudit(
            eventType = "COMMAND_REJECTED",
            actorId = command.rejectedBy,
            actorRole = command.rejectedByRole,
            screenId = command.screenId,
            businessReferenceId = approval.businessReferenceId,
            reason = command.rejectReason,
            payload = mapOf("approvalId" to approvalId, "businessType" to approval.businessType)
        )
        return rejected
    }

    override fun approval(approvalId: String): OperatorApproval =
        approvals[approvalId] ?: throw WorkflowErrors.notFound("approval not found: $approvalId")

    override fun list(): List<OperatorApproval> = approvals.values.toList()

    override fun auditEvents(): List<ApprovalAuditEvent> = auditEvents.toList()

    private fun appendAudit(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String?,
        businessReferenceId: String,
        reason: String?,
        payload: Map<String, Any?>
    ): ApprovalAuditEvent {
        val event = ApprovalAuditEvent(
            auditEventId = "AUD-${nextAuditNumber.toString().padStart(8, '0')}",
            eventType = eventType,
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            reason = reason,
            payload = payload,
            createdAt = now()
        )
        nextAuditNumber += 1
        auditEvents += event
        return event
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }
}
