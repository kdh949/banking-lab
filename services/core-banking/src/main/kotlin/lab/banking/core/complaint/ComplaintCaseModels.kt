package lab.banking.core.complaint

import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.temporal.TemporalWorkflowReference

data class ComplaintCaseDto(
    val caseId: String,
    val customerId: String,
    val category: String,
    val description: String,
    val status: String,
    val slaDueAt: OffsetDateTime,
    val classification: String?,
    val owner: String?,
    val answer: ComplaintAnswerDto?,
    val answerDraft: ComplaintAnswerDraftDto?,
    val approvalId: String?,
    val customerConfirmedAt: OffsetDateTime?,
    val timeline: List<ComplaintTimelineEntryDto>,
    val temporalWorkflow: TemporalWorkflowReference?
)

data class ComplaintTimelineEntryDto(
    val type: String,
    val from: String?,
    val to: String,
    val note: String?,
    val at: OffsetDateTime
)

data class ComplaintAnswerDto(
    val body: String,
    val answeredBy: String,
    val answeredAt: OffsetDateTime
)

data class ComplaintAnswerDraftDto(
    val body: String,
    val draftedBy: String,
    val draftedAt: OffsetDateTime
)

data class ComplaintAnswerDraftCommand(
    val actorId: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val body: String? = null
)

data class ComplaintAnswerDraftResponse(
    val item: ComplaintCaseDto,
    val approval: OperatorApproval
)

data class CustomerComplaintEntryCommand(
    val customerId: String? = null,
    val category: String? = null,
    val description: String? = null,
    val requestedBy: String? = null,
    val reason: String? = null
)

data class CustomerComplaintEntryResponse(
    val item: ComplaintCaseDto
)

data class CustomerComplaintListResponse(
    val items: List<ComplaintCaseDto>
)

data class CustomerComplaintConfirmCommand(
    val customerId: String? = null,
    val note: String? = null,
    val reason: String? = null
)

data class CustomerComplaintConfirmResponse(
    val item: ComplaintCaseDto
)
