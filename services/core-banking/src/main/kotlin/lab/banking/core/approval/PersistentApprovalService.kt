package lab.banking.core.approval

import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class PersistentApprovalService(
    private val repository: ApprovalRepository
) : ApprovalServicePort {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun submit(command: SubmitApprovalCommand): OperatorApproval {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
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

        val approvalId = repository.nextApprovalId()
        val event = repository.appendAudit(
            eventType = "COMMAND_REQUESTED",
            actorId = command.requestedBy,
            actorRole = command.requestedByRole,
            screenId = command.screenId,
            businessReferenceId = command.businessReferenceId,
            reason = command.requestReason,
            payload = mapOf("approvalId" to approvalId, "businessType" to command.businessType, "syntheticOnly" to true)
        )
        val approval = repository.insertPending(approvalId, command, event.auditEventId)
        return approval.copy(auditEventId = event.auditEventId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun approve(approvalId: String, command: ApproveApprovalCommand): OperatorApproval {
        BankingLabAuthContext.requireActor(command.approvedBy, command.approvedByRole)
        requireNonBlank(command.approvedBy, "approvedBy")
        val approval = repository.findForUpdate(approvalId)
        if (approval.status != ApprovalStatus.PENDING) {
            throw WorkflowErrors.stateViolation("only pending approvals can be approved")
        }
        if (approval.requestedBy == command.approvedBy) {
            throw WorkflowErrors.selfApprovalRejected()
        }
        repository.approve(approvalId, command)
        repository.appendAudit(
            eventType = "COMMAND_APPROVED",
            actorId = command.approvedBy,
            actorRole = command.approvedByRole,
            screenId = command.screenId,
            businessReferenceId = approval.businessReferenceId,
            reason = null,
            payload = mapOf("approvalId" to approvalId, "businessType" to approval.businessType, "syntheticOnly" to true)
        )
        return repository.find(approvalId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    override fun reject(approvalId: String, command: RejectApprovalCommand): OperatorApproval {
        BankingLabAuthContext.requireActor(command.rejectedBy, command.rejectedByRole)
        requireNonBlank(command.rejectedBy, "rejectedBy")
        requireNonBlank(command.rejectReason, "rejectReason")
        val approval = repository.findForUpdate(approvalId)
        if (approval.status != ApprovalStatus.PENDING) {
            throw WorkflowErrors.stateViolation("only pending approvals can be rejected")
        }
        repository.reject(approvalId, command)
        repository.appendAudit(
            eventType = "COMMAND_REJECTED",
            actorId = command.rejectedBy,
            actorRole = command.rejectedByRole,
            screenId = command.screenId,
            businessReferenceId = approval.businessReferenceId,
            reason = command.rejectReason,
            payload = mapOf("approvalId" to approvalId, "businessType" to approval.businessType, "syntheticOnly" to true)
        )
        return repository.find(approvalId)
    }

    @Transactional(readOnly = true)
    override fun approval(approvalId: String): OperatorApproval =
        repository.find(approvalId)

    @Transactional(readOnly = true)
    override fun list(): List<OperatorApproval> =
        repository.list()

    @Transactional(readOnly = true)
    override fun auditEvents(): List<ApprovalAuditEvent> =
        repository.auditEvents()

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }
}
