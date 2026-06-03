package lab.banking.core.temporal

import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class TemporalWorkflowReference(
    val workflowId: String,
    val runId: String?
)

data class AttachTemporalWorkflowReferenceCommand(
    val businessType: TemporalBankingCaseType,
    val businessReferenceId: String,
    val workflowId: String,
    val runId: String? = null
)

@Service
class TemporalWorkflowReferenceService(
    private val jdbc: NamedParameterJdbcTemplate
) {
    @Transactional
    fun attach(command: AttachTemporalWorkflowReferenceCommand): TemporalWorkflowReference {
        requireNonBlank(command.businessReferenceId, "businessReferenceId")
        requireNonBlank(command.workflowId, "workflowId")
        val rows = when (command.businessType) {
            TemporalBankingCaseType.COMPLAINT_ANSWER -> updateComplaint(command)
            TemporalBankingCaseType.FDS_RELEASE,
            TemporalBankingCaseType.FDS_BLOCK -> updateFds(command)
            TemporalBankingCaseType.AML_CLOSURE -> updateAml(command)
            TemporalBankingCaseType.RECONCILIATION_ADJUSTMENT -> updateReconciliationAdjustment(command)
            TemporalBankingCaseType.ACCOUNT_HOLD,
            TemporalBankingCaseType.ACCOUNT_RELEASE -> updateAccountHold(command)
        }
        if (rows != 1) {
            throw WorkflowErrors.notFound(
                "Temporal target not found for ${command.businessType.name}: ${command.businessReferenceId}"
            )
        }
        return TemporalWorkflowReference(command.workflowId, command.runId)
    }

    private fun updateComplaint(command: AttachTemporalWorkflowReferenceCommand): Int =
        jdbc.update(
            """
            UPDATE complaint_cases
            SET temporal_workflow_id = :workflowId,
                temporal_run_id = :runId,
                updated_at = now()
            WHERE complaint_case_id = :referenceId
            """.trimIndent(),
            params(command)
        )

    private fun updateFds(command: AttachTemporalWorkflowReferenceCommand): Int =
        jdbc.update(
            """
            UPDATE fds_cases
            SET temporal_workflow_id = :workflowId,
                temporal_run_id = :runId,
                updated_at = now()
            WHERE fds_case_id = :referenceId
            """.trimIndent(),
            params(command)
        )

    private fun updateAml(command: AttachTemporalWorkflowReferenceCommand): Int =
        jdbc.update(
            """
            UPDATE aml_cases
            SET temporal_workflow_id = :workflowId,
                temporal_run_id = :runId,
                updated_at = now()
            WHERE aml_case_id = :referenceId
            """.trimIndent(),
            params(command)
        )

    private fun updateReconciliationAdjustment(command: AttachTemporalWorkflowReferenceCommand): Int =
        jdbc.update(
            """
            UPDATE reconciliation_adjustment_requests
            SET temporal_workflow_id = :workflowId,
                temporal_run_id = :runId,
                updated_at = now()
            WHERE reconciliation_adjustment_request_id = :referenceId
            """.trimIndent(),
            params(command)
        )

    private fun updateAccountHold(command: AttachTemporalWorkflowReferenceCommand): Int =
        jdbc.update(
            """
            UPDATE account_holds
            SET temporal_workflow_id = :workflowId,
                temporal_run_id = :runId
            WHERE hold_id = :referenceId
            """.trimIndent(),
            params(command)
        )

    private fun params(command: AttachTemporalWorkflowReferenceCommand): Map<String, Any?> =
        mapOf(
            "referenceId" to command.businessReferenceId,
            "workflowId" to command.workflowId,
            "runId" to command.runId
        )

    private fun requireNonBlank(value: String?, field: String) {
        if (value.isNullOrBlank()) {
            throw WorkflowErrors.validation("$field is required")
        }
    }
}
