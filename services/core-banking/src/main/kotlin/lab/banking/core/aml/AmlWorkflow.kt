package lab.banking.core.aml

import java.time.Clock
import java.time.OffsetDateTime
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApprovalServicePort
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.workflow.WorkflowErrors

enum class AmlCaseStatus {
    OPEN,
    INVESTIGATING,
    CLOSURE_REQUESTED,
    CLOSED
}

data class AmlAlert(
    val ruleId: String,
    val message: String
)

data class AmlComment(
    val actorId: String,
    val body: String,
    val createdAt: OffsetDateTime
)

data class StrSimulation(
    val reported: Boolean = false,
    val disposition: String? = null,
    val reportReferenceId: String? = null
)

data class AmlCase(
    val caseId: String,
    val customerId: String,
    val status: AmlCaseStatus,
    val riskScore: Int,
    val alerts: List<AmlAlert>,
    val owner: String? = null,
    val approvalId: String? = null,
    val comments: List<AmlComment> = emptyList(),
    val strSimulation: StrSimulation = StrSimulation(),
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class AmlClosureRequest(
    val item: AmlCase,
    val approval: OperatorApproval
)

class AmlWorkflow(private val approvalStore: ApprovalServicePort, private val clock: Clock = Clock.systemUTC()) {
    private var nextCaseNumber = 1

    fun openHighRiskCustomerCase(customerId: String, transferReferenceId: String? = null): AmlCase {
        val caseId = "AML-${nextCaseNumber.toString().padStart(8, '0')}"
        nextCaseNumber += 1
        val now = now()
        return AmlCase(
            caseId = caseId,
            customerId = customerId,
            status = AmlCaseStatus.OPEN,
            riskScore = 850,
            alerts = listOf(AmlAlert("AML-RULE-HIGH-RISK-CUSTOMER", "Synthetic high-risk customer transfer")),
            createdAt = now,
            updatedAt = now
        )
    }

    fun assign(case: AmlCase, actorId: String, owner: String): AmlCase {
        requireStatus(case, AmlCaseStatus.OPEN)
        return case.copy(status = AmlCaseStatus.INVESTIGATING, owner = owner, updatedAt = now())
    }

    fun addComment(case: AmlCase, actorId: String, body: String): AmlCase {
        requireStatus(case, AmlCaseStatus.INVESTIGATING)
        if (body.isBlank()) {
            throw WorkflowErrors.validation("body is required")
        }
        return case.copy(
            comments = case.comments + AmlComment(actorId, body, now()),
            updatedAt = now()
        )
    }

    fun requestClosure(
        case: AmlCase,
        actorId: String,
        reason: String,
        disposition: String,
        reportReferenceId: String?,
        requestedByRole: String = "FDS_REVIEWER"
    ): AmlClosureRequest {
        requireStatus(case, AmlCaseStatus.INVESTIGATING)
        if (disposition.isBlank()) {
            throw WorkflowErrors.validation("disposition is required")
        }
        val closureRequested = case.copy(
            status = AmlCaseStatus.CLOSURE_REQUESTED,
            strSimulation = StrSimulation(
                reported = false,
                disposition = disposition,
                reportReferenceId = reportReferenceId
            ),
            updatedAt = now()
        )
        val approval = approvalStore.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.AML_CASE_CLOSE,
                businessReferenceId = case.caseId,
                requestedBy = actorId,
                requestReason = reason,
                requestedByRole = requestedByRole,
                beforeSnapshot = mapOf("status" to case.status.name),
                afterSnapshot = mapOf("status" to AmlCaseStatus.CLOSED.name, "disposition" to disposition),
                screenId = "AML-201"
            )
        )
        return AmlClosureRequest(closureRequested.copy(approvalId = approval.approvalId), approval)
    }

    fun applyApprovedClosure(case: AmlCase, approval: OperatorApproval): AmlCase {
        requireStatus(case, AmlCaseStatus.CLOSURE_REQUESTED)
        requireApproval(approval, case.caseId, ApprovalBusinessTypes.AML_CASE_CLOSE)
        return case.copy(
            status = AmlCaseStatus.CLOSED,
            approvalId = null,
            strSimulation = case.strSimulation.copy(reported = case.strSimulation.disposition == "STR_SIMULATED"),
            updatedAt = now()
        )
    }

    private fun requireStatus(case: AmlCase, status: AmlCaseStatus) {
        if (case.status != status) {
            throw WorkflowErrors.stateViolation("invalid AML transition ${case.status} -> $status")
        }
    }

    private fun requireApproval(approval: OperatorApproval, referenceId: String, businessType: String) {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessReferenceId != referenceId || approval.businessType != businessType) {
            throw WorkflowErrors.stateViolation("approval does not match AML workflow action")
        }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
