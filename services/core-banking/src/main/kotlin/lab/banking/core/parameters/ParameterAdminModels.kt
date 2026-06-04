package lab.banking.core.parameters

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval

data class ParameterValueDto(
    val namespace: String,
    val parameterKey: String,
    val currentValue: String,
    val currentVersionId: String,
    val valueType: String,
    val effectiveFrom: LocalDate,
    val scheduled: List<ParameterVersionDto>,
    val syntheticOnly: Boolean = true
)

data class ParameterVersionDto(
    val namespace: String,
    val parameterVersionId: String,
    val parameterKey: String,
    val parameterValue: String,
    val valueType: String,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
    val approvalId: String?,
    val createdBy: String,
    val approvedAt: OffsetDateTime?,
    val rollbackOfVersionId: String?,
    val createdAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class ParameterListResponse(
    val auditEventId: String,
    val items: List<ParameterValueDto>
)

data class ParameterHistoryResponse(
    val auditEventId: String,
    val items: List<ParameterVersionDto>
)

data class ParameterChangeRequestCommand(
    val parameterKey: String? = null,
    val scheduledValue: Any? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveAt: OffsetDateTime? = null,
    val rollbackOfVersionId: String? = null,
    val rollbackPlan: String? = null,
    val requestedBy: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null,
    val idempotencyKey: String? = null
)

data class ParameterChangeRequestDto(
    val requestId: String,
    val namespace: String,
    val parameterKey: String,
    val businessType: String,
    val approvalId: String,
    val requestedValue: String,
    val valueType: String,
    val effectiveFrom: LocalDate,
    val rollbackPlan: String,
    val rollbackOfVersionId: String?,
    val requestedBy: String,
    val requestedRole: String,
    val reason: String,
    val status: String,
    val idempotencyKey: String,
    val appliedVersionId: String?,
    val appliedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val syntheticOnly: Boolean = true
)

data class ParameterChangeRequestResponse(
    val item: ParameterChangeRequestDto,
    val approval: OperatorApproval?,
    val replayed: Boolean
)
