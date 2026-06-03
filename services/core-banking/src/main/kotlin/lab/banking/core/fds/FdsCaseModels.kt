package lab.banking.core.fds

import java.time.LocalDate
import java.time.OffsetDateTime
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.temporal.TemporalWorkflowReference

data class FdsCaseDto(
    val caseId: String,
    val transferReferenceId: String,
    val customerId: String,
    val status: String,
    val riskScore: Int,
    val alerts: List<FdsAlert>,
    val owner: String?,
    val approvalId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amountMinor: Long?,
    val transferIdempotencyKey: String?,
    val requestedBy: String?,
    val businessDate: LocalDate?,
    val transferStatus: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val temporalWorkflow: TemporalWorkflowReference?
)

data class FdsDecisionCommand(
    val actorId: String? = null,
    val requestedByRole: String? = null,
    val reason: String? = null
)

data class FdsAssignCommand(
    val actorId: String? = null,
    val actorRole: String? = null,
    val owner: String? = null,
    val reason: String? = null
)

data class FdsDecisionRequestResponse(
    val item: FdsCaseDto,
    val approval: OperatorApproval
)

data class FdsDecisionExecutionResponse(
    val item: FdsCaseDto,
    val ledgerTransaction: LedgerCommandResult?
)
