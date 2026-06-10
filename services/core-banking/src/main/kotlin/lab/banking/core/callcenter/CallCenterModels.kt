package lab.banking.core.callcenter

import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval

data class CallCenterCustomerSearchResponse(
    val auditEventId: String,
    val items: List<CallCenterCustomerSummaryDto>
)

data class CallCenterCustomerSummaryDto(
    val customerId: String,
    val maskedName: String,
    val maskedPhone: String?,
    val customerGrade: String,
    val riskGrade: String
)

data class StartCallCenterInteractionCommand(
    val customerId: String? = null,
    val accountId: String? = null,
    val channel: String? = null,
    val contactReasonCode: String? = null,
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val assignedTo: String? = null,
    val reason: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class CallCenterNoteCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val noteBody: String? = null
)

data class CallCenterAftercallTaskCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val taskType: String? = null,
    val assignedTo: String? = null,
    val dueAt: OffsetDateTime? = null,
    val metadata: Map<String, Any?>? = null
)

data class CallCenterEscalationCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val escalationType: String? = null,
    val complaintCategory: String? = null,
    val complaintDescription: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class CloseCallCenterInteractionCommand(
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null
)

data class CallCenterInteractionResponse(
    val item: CallCenterInteractionDto
)

data class CallCenterInteractionListResponse(
    val auditEventId: String,
    val items: List<CallCenterInteractionSummaryDto>
)

data class CallCenterInteractionSummaryDto(
    val interactionId: String,
    val customerId: String,
    val maskedCustomerName: String,
    val channel: String,
    val contactReasonCode: String,
    val status: String,
    val assignedTo: String?,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?
)

data class CallCenterInteractionDto(
    val interactionId: String,
    val customerId: String,
    val accountId: String?,
    val channel: String,
    val contactReasonCode: String,
    val status: String,
    val createdBy: String,
    val createdByRole: String,
    val assignedTo: String?,
    val reason: String,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val metadata: Map<String, Any?>,
    val auditEventId: String,
    val notes: List<CallCenterNoteDto>,
    val aftercallTasks: List<CallCenterAftercallTaskDto>,
    val escalations: List<CallCenterEscalationDto>
)

data class CallCenterNoteDto(
    val noteId: String,
    val interactionId: String,
    val customerId: String,
    val createdBy: String,
    val createdByRole: String,
    val noteBodyRedacted: String,
    val redactionApplied: Boolean,
    val piiPatternCount: Int,
    val reason: String,
    val auditEventId: String,
    val createdAt: OffsetDateTime
)

data class CallCenterNoteResponse(
    val item: CallCenterInteractionDto,
    val note: CallCenterNoteDto
)

data class CallCenterAftercallTaskDto(
    val taskId: String,
    val interactionId: String,
    val customerId: String,
    val taskType: String,
    val status: String,
    val assignedTo: String?,
    val dueAt: OffsetDateTime?,
    val createdBy: String,
    val createdByRole: String,
    val reason: String,
    val auditEventId: String,
    val metadata: Map<String, Any?>,
    val createdAt: OffsetDateTime
)

data class CallCenterAftercallTaskResponse(
    val item: CallCenterInteractionDto,
    val task: CallCenterAftercallTaskDto
)

data class CallCenterEscalationDto(
    val escalationId: String,
    val interactionId: String,
    val customerId: String,
    val escalationType: String,
    val status: String,
    val complaintCaseId: String?,
    val requestedBy: String,
    val requestedByRole: String,
    val reason: String,
    val approvalId: String?,
    val auditEventId: String,
    val metadata: Map<String, Any?>,
    val createdAt: OffsetDateTime
)

data class CallCenterEscalationResponse(
    val item: CallCenterInteractionDto,
    val escalation: CallCenterEscalationDto,
    val approval: OperatorApproval? = null
)
