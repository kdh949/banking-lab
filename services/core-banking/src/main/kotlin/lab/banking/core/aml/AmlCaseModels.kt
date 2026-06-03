package lab.banking.core.aml

import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.temporal.TemporalWorkflowReference

data class AmlCaseDto(
    val caseId: String,
    val customerId: String,
    val status: String,
    val riskScore: Int,
    val alerts: List<AmlAlert>,
    val owner: String?,
    val approvalId: String?,
    val comments: List<AmlCommentDto>,
    val strSimulation: AmlStrSimulationDto,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val temporalWorkflow: TemporalWorkflowReference?
)

data class AmlCommentDto(
    val actorId: String,
    val body: String,
    val createdAt: OffsetDateTime
)

data class AmlStrSimulationDto(
    val reported: Boolean = false,
    val disposition: String? = null,
    val reportReferenceId: String? = null
)

data class AmlAssignCommand(
    val actorId: String? = null,
    val owner: String? = null
)

data class AmlCommentCommand(
    val actorId: String? = null,
    val body: String? = null
)

data class AmlClosureCommand(
    val actorId: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val disposition: String? = null,
    val reportReferenceId: String? = null
)

data class AmlClosureRequestResponse(
    val item: AmlCaseDto,
    val approval: OperatorApproval
)
