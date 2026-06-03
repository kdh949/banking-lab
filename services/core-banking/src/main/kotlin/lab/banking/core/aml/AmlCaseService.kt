package lab.banking.core.aml

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.temporal.TemporalWorkflowReference
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class AmlCaseService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val transactionManager: PlatformTransactionManager
) {
    @Transactional(readOnly = true)
    fun list(): List<AmlCaseDto> =
        jdbc.query(
            amlSql("ORDER BY created_at, aml_case_id"),
            emptyMap<String, Any?>(),
            this::mapCase
        )

    @Transactional(readOnly = true)
    fun find(caseId: String): AmlCaseDto =
        findForRead(caseId)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun assign(caseId: String, command: AmlAssignCommand): AmlCaseDto {
        val owner = command.owner
        if (owner.isNullOrBlank()) {
            throw WorkflowErrors.validation("owner is required")
        }
        val amlCase = findForUpdate(caseId)
        if (amlCase.status != "OPEN") {
            throw WorkflowErrors.stateViolation("AML case is not open: ${amlCase.status}")
        }
        jdbc.update(
            """
            UPDATE aml_cases
            SET status = 'INVESTIGATING',
                owner_id = :owner,
                updated_at = now()
            WHERE aml_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to caseId, "owner" to owner)
        )
        return findForRead(caseId)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun addComment(caseId: String, command: AmlCommentCommand): AmlCaseDto {
        if (command.body.isNullOrBlank()) {
            throw WorkflowErrors.validation("body is required")
        }
        val amlCase = findForUpdate(caseId)
        if (amlCase.status != "INVESTIGATING") {
            throw WorkflowErrors.stateViolation("AML case is not investigating: ${amlCase.status}")
        }
        jdbc.update(
            """
            INSERT INTO aml_case_comments (aml_comment_id, aml_case_id, actor_id, body)
            VALUES (:commentId, :caseId, :actorId, :body)
            """.trimIndent(),
            mapOf(
                "commentId" to "AMC-${UUID.randomUUID().toString().uppercase()}",
                "caseId" to caseId,
                "actorId" to (command.actorId ?: "aml01"),
                "body" to command.body
            )
        )
        return findForRead(caseId)
    }

    fun requestClosure(caseId: String, command: AmlClosureCommand): AmlClosureRequestResponse {
        return runSerializableClosureRequest {
            requestClosureInTransaction(caseId, command)
        }
    }

    private fun requestClosureInTransaction(caseId: String, command: AmlClosureCommand): AmlClosureRequestResponse {
        if (command.reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("AML closure requires a business reason")
        }
        val disposition = command.disposition ?: "FALSE_POSITIVE"
        if (disposition.isBlank()) {
            throw WorkflowErrors.validation("disposition is required")
        }
        val amlCase = findForUpdate(caseId)
        if (amlCase.status != "INVESTIGATING") {
            throw WorkflowErrors.stateViolation("AML case is not investigating: ${amlCase.status}")
        }
        val actorId = command.actorId ?: "aml01"
        val strSimulation = AmlStrSimulationDto(
            reported = false,
            disposition = disposition,
            reportReferenceId = command.reportReferenceId
        )
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.AML_CASE_CLOSE,
                businessReferenceId = caseId,
                requestedBy = actorId,
                requestedByRole = command.requestedByRole ?: "AML_REVIEWER",
                requestReason = command.reason,
                beforeSnapshot = mapOf("status" to amlCase.status, "strSimulation" to amlCase.strSimulation),
                afterSnapshot = mapOf("status" to "CLOSED", "disposition" to disposition, "reportReferenceId" to command.reportReferenceId),
                screenId = "AML-201"
            )
        )
        jdbc.update(
            """
            UPDATE aml_cases
            SET status = 'CLOSURE_REQUESTED',
                approval_id = :approvalId,
                str_simulation_json = CAST(:strSimulationJson AS jsonb),
                updated_at = now()
            WHERE aml_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "approvalId" to approval.approvalId,
                "strSimulationJson" to objectMapper.writeValueAsString(strSimulation)
            )
        )
        return AmlClosureRequestResponse(item = findForRead(caseId), approval = approval)
    }

    private fun <T> runSerializableClosureRequest(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable AML closure request returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_AML_CLOSURE_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
                    throw error
                }
                Thread.sleep(75L * attempt * attempt)
                attempt += 1
            }
        }
    }

    private fun isRetryableSerializationFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is TransientDataAccessException || current is PessimisticLockingFailureException) {
                return true
            }
            val message = current.message.orEmpty()
            if (
                message.contains("could not serialize access", ignoreCase = true) ||
                message.contains("SQLSTATE 40001", ignoreCase = true) ||
                message.contains("deadlock detected", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedClosure(approval: OperatorApproval): AmlCaseDto {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessType != ApprovalBusinessTypes.AML_CASE_CLOSE) {
            throw WorkflowErrors.stateViolation("approval is not an AML closure approval")
        }
        val amlCase = findForUpdate(approval.businessReferenceId)
        if (amlCase.status != "CLOSURE_REQUESTED") {
            throw WorkflowErrors.stateViolation("AML case is not closure requested: ${amlCase.status}")
        }
        val closedSimulation = amlCase.strSimulation.copy(
            reported = amlCase.strSimulation.disposition == "STR_SIMULATED"
        )
        jdbc.update(
            """
            UPDATE aml_cases
            SET status = 'CLOSED',
                approval_id = NULL,
                str_simulation_json = CAST(:strSimulationJson AS jsonb),
                updated_at = now()
            WHERE aml_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to amlCase.caseId,
                "strSimulationJson" to objectMapper.writeValueAsString(closedSimulation)
            )
        )
        return findForRead(amlCase.caseId)
    }

    private fun findForRead(caseId: String): AmlCaseDto =
        jdbc.queryForObject(
            amlSql("WHERE aml_case_id = :caseId"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("AML case not found: $caseId")

    private fun findForUpdate(caseId: String): AmlCaseDto =
        jdbc.queryForObject(
            amlSql("WHERE aml_case_id = :caseId FOR UPDATE"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("AML case not found: $caseId")

    private fun amlSql(suffix: String): String =
        """
        SELECT aml_case_id, customer_id, status, risk_score, alerts_json::text AS alerts_json,
               owner_id, approval_id, str_simulation_json::text AS str_simulation_json,
               temporal_workflow_id, temporal_run_id, created_at, updated_at
        FROM aml_cases
        $suffix
        """.trimIndent()

    private fun mapCase(rs: ResultSet, rowNum: Int): AmlCaseDto {
        val caseId = rs.getString("aml_case_id")
        return AmlCaseDto(
            caseId = caseId,
            customerId = rs.getString("customer_id"),
            status = rs.getString("status"),
            riskScore = rs.getInt("risk_score"),
            alerts = readAlerts(rs.getString("alerts_json")),
            owner = rs.getString("owner_id"),
            approvalId = rs.getString("approval_id"),
            comments = comments(caseId),
            strSimulation = readStrSimulation(rs.getString("str_simulation_json")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            temporalWorkflow = temporalReference(rs)
        )
    }

    private fun comments(caseId: String): List<AmlCommentDto> =
        jdbc.query(
            """
            SELECT actor_id, body, created_at
            FROM aml_case_comments
            WHERE aml_case_id = :caseId
            ORDER BY created_at, aml_comment_id
            """.trimIndent(),
            mapOf("caseId" to caseId)
        ) { rs, _ ->
            AmlCommentDto(
                actorId = rs.getString("actor_id"),
                body = rs.getString("body"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }

    private fun readAlerts(alertsJson: String): List<AmlAlert> =
        objectMapper.readValue(
            alertsJson,
            objectMapper.typeFactory.constructCollectionType(List::class.java, AmlAlert::class.java)
        )

    private fun readStrSimulation(strSimulationJson: String): AmlStrSimulationDto =
        objectMapper.readValue(strSimulationJson, AmlStrSimulationDto::class.java)

    private fun temporalReference(rs: ResultSet): TemporalWorkflowReference? {
        val workflowId = rs.getString("temporal_workflow_id") ?: return null
        return TemporalWorkflowReference(
            workflowId = workflowId,
            runId = rs.getString("temporal_run_id")
        )
    }

    private companion object {
        const val SERIALIZABLE_AML_CLOSURE_MAX_ATTEMPTS = 5
    }
}
