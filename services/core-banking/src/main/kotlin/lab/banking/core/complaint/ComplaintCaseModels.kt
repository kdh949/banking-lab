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

data class CustomerComplaintMaterialCommand(
    val customerId: String? = null,
    val materialType: String? = null,
    val fileName: String? = null,
    val description: String? = null,
    val syntheticStorageRef: String? = null,
    val reason: String? = null
)

data class ComplaintMaterialDto(
    val materialId: String,
    val caseId: String,
    val customerId: String,
    val materialType: String,
    val fileName: String,
    val description: String?,
    val syntheticStorageRef: String,
    val submittedBy: String,
    val createdAt: OffsetDateTime
)

data class CustomerComplaintMaterialResponse(
    val item: ComplaintCaseDto,
    val material: ComplaintMaterialDto
)

data class CustomerComplaintReopenCommand(
    val customerId: String? = null,
    val reopenReason: String? = null,
    val reason: String? = null
)

data class ComplaintReopenRequestDto(
    val reopenRequestId: String,
    val caseId: String,
    val customerId: String,
    val reopenReason: String,
    val status: String,
    val requestedBy: String,
    val createdAt: OffsetDateTime
)

data class CustomerComplaintReopenResponse(
    val item: ComplaintCaseDto,
    val reopenRequest: ComplaintReopenRequestDto
)

data class ComplaintTypeGuideDto(
    val category: String,
    val description: String,
    val slaHours: Int,
    val requiredMaterials: List<String>
)

data class ComplaintTypeGuideResponse(
    val items: List<ComplaintTypeGuideDto>
)
