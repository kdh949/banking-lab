package lab.banking.core.complaint

import java.time.Clock
import java.time.OffsetDateTime
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.ApprovalServicePort
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.workflow.WorkflowErrors

enum class ComplaintStatus {
    RECEIVED,
    CLASSIFIED,
    ASSIGNED,
    IN_REVIEW,
    WAITING_CUSTOMER,
    WAITING_APPROVAL,
    ANSWERED,
    CLOSED,
    REOPENED,
    TRANSFERRED_TO_AUTHORITY_SIM
}

data class ComplaintAnswer(
    val body: String,
    val answeredBy: String,
    val answeredAt: OffsetDateTime
)

data class ComplaintTimelineEntry(
    val type: String,
    val from: ComplaintStatus?,
    val to: ComplaintStatus,
    val actorId: String?,
    val note: String?,
    val at: OffsetDateTime
)

data class ComplaintCase(
    val caseId: String,
    val customerId: String,
    val category: String,
    val description: String,
    val status: ComplaintStatus,
    val slaDueAt: OffsetDateTime,
    val classification: String? = null,
    val owner: String? = null,
    val answer: ComplaintAnswer? = null,
    val answerDraft: String? = null,
    val approvalId: String? = null,
    val customerConfirmedAt: OffsetDateTime? = null,
    val timeline: List<ComplaintTimelineEntry> = emptyList()
) {
    fun customerView(): CustomerComplaintView =
        CustomerComplaintView(
            caseId = caseId,
            customerId = customerId,
            category = category,
            description = description,
            status = status,
            slaDueAt = slaDueAt,
            answer = answer,
            customerConfirmedAt = customerConfirmedAt,
            timeline = timeline.map {
                CustomerComplaintTimelineEntry(
                    type = it.type,
                    from = it.from,
                    to = it.to,
                    note = it.note,
                    at = it.at
                )
            }
        )
}

data class CustomerComplaintTimelineEntry(
    val type: String,
    val from: ComplaintStatus?,
    val to: ComplaintStatus,
    val note: String?,
    val at: OffsetDateTime
)

data class CustomerComplaintView(
    val caseId: String,
    val customerId: String,
    val category: String,
    val description: String,
    val status: ComplaintStatus,
    val slaDueAt: OffsetDateTime,
    val answer: ComplaintAnswer?,
    val customerConfirmedAt: OffsetDateTime?,
    val timeline: List<CustomerComplaintTimelineEntry>
)

data class ComplaintApprovalRequest(
    val item: ComplaintCase,
    val approval: OperatorApproval
)

class ComplaintWorkflow(private val approvalStore: ApprovalServicePort, private val clock: Clock = Clock.systemUTC()) {
    private var nextCaseNumber = 1

    fun create(customerId: String, category: String, description: String): ComplaintCase {
        requireNonBlank(customerId, "customerId")
        requireNonBlank(category, "category")
        requireNonBlank(description, "description")
        val caseId = "CMP-${nextCaseNumber.toString().padStart(8, '0')}"
        nextCaseNumber += 1
        val now = now()
        return ComplaintCase(
            caseId = caseId,
            customerId = customerId,
            category = category,
            description = description,
            status = ComplaintStatus.RECEIVED,
            slaDueAt = now.plusDays(7),
            timeline = listOf(ComplaintTimelineEntry("RECEIVED", null, ComplaintStatus.RECEIVED, null, null, now))
        )
    }

    fun classify(case: ComplaintCase, actorId: String, classification: String, note: String? = null): ComplaintCase {
        requireTransition(case.status, ComplaintStatus.CLASSIFIED)
        return transition(case, ComplaintStatus.CLASSIFIED, actorId, note).copy(classification = classification)
    }

    fun assign(case: ComplaintCase, actorId: String, owner: String): ComplaintCase {
        requireTransition(case.status, ComplaintStatus.ASSIGNED)
        requireNonBlank(owner, "owner")
        return transition(case, ComplaintStatus.ASSIGNED, actorId, null).copy(owner = owner)
    }

    fun startReview(case: ComplaintCase, actorId: String, note: String? = null): ComplaintCase {
        requireTransition(case.status, ComplaintStatus.IN_REVIEW)
        return transition(case, ComplaintStatus.IN_REVIEW, actorId, note)
    }

    fun draftAnswer(
        case: ComplaintCase,
        actorId: String,
        requestedByRole: String = "COMPLAINT_HANDLER",
        reason: String,
        body: String
    ): ComplaintApprovalRequest {
        requireTransition(case.status, ComplaintStatus.WAITING_APPROVAL)
        requireNonBlank(body, "body")
        val waiting = transition(case, ComplaintStatus.WAITING_APPROVAL, actorId, "answer draft prepared")
            .copy(answerDraft = body)
        val approval = approvalStore.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.COMPLAINT_ANSWER_SEND,
                businessReferenceId = case.caseId,
                requestedBy = actorId,
                requestReason = reason,
                requestedByRole = requestedByRole,
                beforeSnapshot = mapOf("status" to case.status.name),
                afterSnapshot = mapOf("status" to ComplaintStatus.ANSWERED.name),
                screenId = "CMP-201"
            )
        )
        return ComplaintApprovalRequest(waiting.copy(approvalId = approval.approvalId), approval)
    }

    fun applyApprovedAnswer(case: ComplaintCase, approval: OperatorApproval): ComplaintCase {
        if (case.status != ComplaintStatus.WAITING_APPROVAL) {
            throw WorkflowErrors.stateViolation("complaint is not waiting for answer approval: ${case.status}")
        }
        requireApproval(approval, case.caseId, ApprovalBusinessTypes.COMPLAINT_ANSWER_SEND)
        val draft = case.answerDraft ?: throw WorkflowErrors.stateViolation("complaint answer draft is missing")
        val answeredAt = now()
        return transition(case, ComplaintStatus.ANSWERED, approval.approvedBy, "answer sent")
            .copy(
                answer = ComplaintAnswer(draft, approval.approvedBy ?: "CHECKER", answeredAt),
                answerDraft = null,
                approvalId = null
            )
    }

    fun customerConfirm(case: ComplaintCase, customerId: String, note: String? = null): ComplaintCase {
        if (case.customerId != customerId) {
            throw WorkflowErrors.notFound("complaint not found for customer: ${case.caseId}")
        }
        requireTransition(case.status, ComplaintStatus.CLOSED)
        return transition(case, ComplaintStatus.CLOSED, customerId, note)
            .copy(customerConfirmedAt = now())
    }

    private fun transition(case: ComplaintCase, next: ComplaintStatus, actorId: String?, note: String?): ComplaintCase =
        case.copy(
            status = next,
            timeline = case.timeline + ComplaintTimelineEntry(next.name, case.status, next, actorId, note, now())
        )

    private fun requireTransition(current: ComplaintStatus, next: ComplaintStatus) {
        val allowed = mapOf(
            ComplaintStatus.RECEIVED to setOf(ComplaintStatus.CLASSIFIED),
            ComplaintStatus.CLASSIFIED to setOf(ComplaintStatus.ASSIGNED),
            ComplaintStatus.ASSIGNED to setOf(ComplaintStatus.IN_REVIEW),
            ComplaintStatus.IN_REVIEW to setOf(ComplaintStatus.WAITING_CUSTOMER, ComplaintStatus.WAITING_APPROVAL),
            ComplaintStatus.WAITING_CUSTOMER to setOf(ComplaintStatus.IN_REVIEW),
            ComplaintStatus.WAITING_APPROVAL to setOf(ComplaintStatus.ANSWERED, ComplaintStatus.IN_REVIEW),
            ComplaintStatus.ANSWERED to setOf(ComplaintStatus.CLOSED),
            ComplaintStatus.CLOSED to setOf(ComplaintStatus.REOPENED),
            ComplaintStatus.REOPENED to setOf(ComplaintStatus.IN_REVIEW, ComplaintStatus.TRANSFERRED_TO_AUTHORITY_SIM)
        )
        if (!allowed.getOrDefault(current, emptySet()).contains(next)) {
            throw WorkflowErrors.stateViolation("invalid complaint transition $current -> $next")
        }
    }

    private fun requireApproval(approval: OperatorApproval, referenceId: String, businessType: String) {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessReferenceId != referenceId || approval.businessType != businessType) {
            throw WorkflowErrors.stateViolation("approval does not match complaint workflow action")
        }
    }

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
