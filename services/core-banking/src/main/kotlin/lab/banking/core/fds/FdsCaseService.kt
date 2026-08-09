package lab.banking.core.fds

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.RejectApprovalCommand
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.ledger.application.InternalTransferCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.journey.BusinessJourneyService
import lab.banking.core.security.BankingLabAuthContext
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
class FdsCaseService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val ledgerCommandService: LedgerCommandService,
    private val transactionManager: PlatformTransactionManager,
    private val journeys: BusinessJourneyService
) {
    @Transactional(readOnly = true)
    fun list(): List<FdsCaseDto> =
        jdbc.query(
            fdsSql("ORDER BY created_at, fds_case_id"),
            emptyMap<String, Any?>(),
            this::mapCase
        )

    @Transactional(readOnly = true)
    fun find(caseId: String): FdsCaseDto =
        findForRead(caseId)

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun assign(caseId: String, command: FdsAssignCommand): FdsCaseDto {
        val actorId = command.actorId ?: command.owner ?: "fds01"
        val actorRole = command.actorRole ?: "FDS_REVIEWER"
        BankingLabAuthContext.requireActor(actorId, actorRole)
        val owner = command.owner?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("owner is required for FDS assignment")
        val fdsCase = findForUpdate(caseId)
        if (fdsCase.status != "HELD") {
            throw WorkflowErrors.stateViolation("FDS case is not held: ${fdsCase.status}")
        }
        jdbc.update(
            """
            UPDATE fds_cases
            SET status = 'INVESTIGATING',
                owner_id = :owner,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to caseId, "owner" to owner)
        )
        appendTimeline(
            caseId = caseId,
            eventType = "INVESTIGATION_STARTED",
            fromStatus = fdsCase.status,
            toStatus = "INVESTIGATING",
            actorId = actorId,
            note = command.reason ?: "FDS investigation assigned"
        )
        fdsCase.journeyId?.let { journeyId ->
            journeys.correlate(
                journeyId = journeyId,
                referenceType = "FDS_CASE",
                referenceId = caseId,
                eventType = "FDS_INVESTIGATION_STARTED",
                status = "INVESTIGATING",
                actorId = actorId,
                actorRole = actorRole,
                reason = command.reason
            )
        }
        return findForRead(caseId)
    }

    fun requestRelease(caseId: String, command: FdsDecisionCommand): FdsDecisionRequestResponse =
        runSerializableDecisionRequest {
            requestDecision(
                caseId = caseId,
                command = command,
                decisionStatus = "RELEASE_REQUESTED",
                businessType = ApprovalBusinessTypes.FDS_RELEASE,
                decision = "RELEASE"
            )
        }

    fun requestBlock(caseId: String, command: FdsDecisionCommand): FdsDecisionRequestResponse =
        runSerializableDecisionRequest {
            requestDecision(
                caseId = caseId,
                command = command,
                decisionStatus = "BLOCK_REQUESTED",
                businessType = ApprovalBusinessTypes.FDS_BLOCK,
                decision = "BLOCK"
            )
        }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedDecision(approval: OperatorApproval): FdsDecisionExecutionResponse {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        return when (approval.businessType) {
            ApprovalBusinessTypes.FDS_RELEASE -> applyApprovedRelease(approval)
            ApprovalBusinessTypes.FDS_BLOCK -> applyApprovedBlock(approval)
            else -> throw WorkflowErrors.stateViolation("approval is not an FDS decision approval")
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyRejectedDecision(approval: OperatorApproval, command: RejectApprovalCommand): FdsCaseDto {
        if (approval.status != ApprovalStatus.REJECTED) {
            throw WorkflowErrors.stateViolation("approval is not rejected: ${approval.status}")
        }
        val expectedStatus = when (approval.businessType) {
            ApprovalBusinessTypes.FDS_RELEASE -> "RELEASE_REQUESTED"
            ApprovalBusinessTypes.FDS_BLOCK -> "BLOCK_REQUESTED"
            else -> throw WorkflowErrors.stateViolation("approval is not an FDS decision approval")
        }
        val fdsCase = findForUpdate(approval.businessReferenceId)
        if (fdsCase.status != expectedStatus) {
            throw WorkflowErrors.stateViolation("FDS case is not decision requested: ${fdsCase.status}")
        }
        if (fdsCase.approvalId != approval.approvalId) {
            throw WorkflowErrors.stateViolation("approval does not match FDS case")
        }
        jdbc.update(
            """
            UPDATE fds_cases
            SET status = 'INVESTIGATING',
                approval_id = NULL,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to fdsCase.caseId)
        )
        appendTimeline(
            caseId = fdsCase.caseId,
            eventType = "DECISION_REJECTED",
            fromStatus = fdsCase.status,
            toStatus = "INVESTIGATING",
            actorId = command.rejectedBy,
            note = command.rejectReason
        )
        fdsCase.journeyId?.let { journeyId ->
            journeys.correlate(
                journeyId = journeyId,
                referenceType = "APPROVAL",
                referenceId = approval.approvalId,
                eventType = "FDS_DECISION_REJECTED",
                status = "INVESTIGATING",
                actorId = command.rejectedBy,
                actorRole = command.rejectedByRole,
                reason = command.rejectReason,
                payload = mapOf(
                    "decision" to if (approval.businessType == ApprovalBusinessTypes.FDS_RELEASE) "RELEASE" else "BLOCK",
                    "approvalDecision" to "REJECTED",
                    "ledgerTransactionCount" to 0
                )
            )
        }
        return findForRead(fdsCase.caseId)
    }

    private fun requestDecision(
        caseId: String,
        command: FdsDecisionCommand,
        decisionStatus: String,
        businessType: String,
        decision: String
    ): FdsDecisionRequestResponse {
        if (command.reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("FDS decision requires a business reason")
        }
        val fdsCase = findForUpdate(caseId)
        if (fdsCase.status != "INVESTIGATING") {
            throw WorkflowErrors.stateViolation("FDS case is not investigating: ${fdsCase.status}")
        }
        val actorId = command.actorId ?: "fds01"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = businessType,
                businessReferenceId = caseId,
                requestedBy = actorId,
                requestedByRole = command.requestedByRole ?: "FDS_REVIEWER",
                requestReason = command.reason,
                beforeSnapshot = mapOf("status" to fdsCase.status, "transferReferenceId" to fdsCase.transferReferenceId),
                afterSnapshot = mapOf(
                    "decision" to decision,
                    "releaseIdempotencyKey" to releaseIdempotencyKey(caseId),
                    "blockReferenceId" to "FDS-BLOCK-$caseId"
                ),
                screenId = "FDS-201"
            )
        )
        jdbc.update(
            """
            UPDATE fds_cases
            SET status = :status,
                approval_id = :approvalId,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to caseId, "status" to decisionStatus, "approvalId" to approval.approvalId)
        )
        appendTimeline(caseId, decisionStatus, fdsCase.status, decisionStatus, actorId, command.reason)
        fdsCase.journeyId?.let { journeyId ->
            journeys.correlate(
                journeyId = journeyId,
                referenceType = "APPROVAL",
                referenceId = approval.approvalId,
                eventType = if (decision == "RELEASE") "FDS_RELEASE_REQUESTED" else "FDS_BLOCK_REQUESTED",
                status = "PENDING_APPROVAL",
                actorId = actorId,
                actorRole = command.requestedByRole ?: "FDS_REVIEWER",
                reason = command.reason,
                payload = mapOf("decision" to decision)
            )
        }
        return FdsDecisionRequestResponse(item = findForRead(caseId), approval = approval)
    }

    private fun <T> runSerializableDecisionRequest(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable FDS decision request returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_FDS_DECISION_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
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

    private fun applyApprovedRelease(approval: OperatorApproval): FdsDecisionExecutionResponse {
        val fdsCase = findForUpdate(approval.businessReferenceId)
        if (fdsCase.status != "RELEASE_REQUESTED") {
            throw WorkflowErrors.stateViolation("FDS case is not release requested: ${fdsCase.status}")
        }
        val ledgerResult = ledgerCommandService.internalTransfer(
            InternalTransferCommand(
                fromAccountId = requireField(fdsCase.fromAccountId, "fromAccountId"),
                toAccountId = requireField(fdsCase.toAccountId, "toAccountId"),
                amountMinor = fdsCase.amountMinor ?: throw WorkflowErrors.validation("amountMinor is required"),
                idempotencyKey = releaseIdempotencyKey(fdsCase.caseId),
                requestedBy = requireField(fdsCase.requestedBy, "requestedBy"),
                requestedChannel = "CUSTOMER_WEB",
                businessDate = fdsCase.businessDate,
                reason = "Approved FDS release ${fdsCase.caseId}",
                businessReferenceId = fdsCase.caseId
            )
        )
        jdbc.update(
            """
            UPDATE fds_cases
            SET status = 'RELEASED',
                transfer_status = 'POSTED',
                approval_id = NULL,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to fdsCase.caseId)
        )
        updateCustomerTransferResult(
            caseId = fdsCase.caseId,
            status = "POSTED",
            ledgerTransactionId = ledgerResult.value.id,
            message = "FDS reviewer released transfer to ledger"
        )
        appendTimeline(fdsCase.caseId, "RELEASED", fdsCase.status, "RELEASED", approval.approvedBy, "release posted")
        fdsCase.journeyId?.let { journeyId ->
            journeys.correlate(
                journeyId = journeyId,
                referenceType = "LEDGER_TRANSACTION",
                referenceId = ledgerResult.value.id,
                eventType = "TRANSFER_POSTED",
                status = "POSTED",
                actorId = approval.approvedBy,
                actorRole = "FDS_APPROVER",
                reason = approval.requestReason,
                payload = mapOf(
                    "ledgerTransactionCount" to 1,
                    "balancedDoubleEntry" to true,
                    "replayed" to ledgerResult.replayed
                )
            )
        }
        return FdsDecisionExecutionResponse(item = findForRead(fdsCase.caseId), ledgerTransaction = ledgerResult)
    }

    private fun applyApprovedBlock(approval: OperatorApproval): FdsDecisionExecutionResponse {
        val fdsCase = findForUpdate(approval.businessReferenceId)
        if (fdsCase.status != "BLOCK_REQUESTED") {
            throw WorkflowErrors.stateViolation("FDS case is not block requested: ${fdsCase.status}")
        }
        jdbc.update(
            """
            UPDATE fds_cases
            SET status = 'BLOCKED',
                transfer_status = 'BLOCKED',
                approval_id = NULL,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to fdsCase.caseId)
        )
        updateCustomerTransferResult(
            caseId = fdsCase.caseId,
            status = "BLOCKED",
            ledgerTransactionId = null,
            message = "FDS reviewer blocked transfer"
        )
        appendTimeline(fdsCase.caseId, "BLOCKED", fdsCase.status, "BLOCKED", approval.approvedBy, "transfer blocked")
        fdsCase.journeyId?.let { journeyId ->
            journeys.correlate(
                journeyId = journeyId,
                referenceType = "FDS_CASE",
                referenceId = fdsCase.caseId,
                eventType = "TRANSFER_BLOCKED",
                status = "BLOCKED",
                actorId = approval.approvedBy,
                actorRole = "FDS_APPROVER",
                reason = approval.requestReason,
                payload = mapOf("ledgerTransactionCount" to 0)
            )
        }
        return FdsDecisionExecutionResponse(item = findForRead(fdsCase.caseId), ledgerTransaction = null)
    }

    private fun updateCustomerTransferResult(
        caseId: String,
        status: String,
        ledgerTransactionId: String?,
        message: String
    ) {
        jdbc.update(
            """
            UPDATE customer_transfer_results
            SET status = :status,
                ledger_transaction_id = COALESCE(:ledgerTransactionId, ledger_transaction_id),
                message = :message,
                updated_at = now()
            WHERE fds_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "status" to status,
                "ledgerTransactionId" to ledgerTransactionId,
                "message" to message
            )
        )
    }

    private fun appendTimeline(
        caseId: String,
        eventType: String,
        fromStatus: String?,
        toStatus: String,
        actorId: String?,
        note: String?
    ) {
        jdbc.update(
            """
            INSERT INTO fds_case_timeline (
              fds_timeline_id, fds_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              :timelineId, :caseId, :eventType, :fromStatus,
              :toStatus, :actorId, :note, '{}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "timelineId" to "FDT-${UUID.randomUUID().toString().uppercase()}",
                "caseId" to caseId,
                "eventType" to eventType,
                "fromStatus" to fromStatus,
                "toStatus" to toStatus,
                "actorId" to actorId,
                "note" to note
            )
        )
    }

    private fun findForRead(caseId: String): FdsCaseDto =
        jdbc.queryForObject(
            fdsSql("WHERE fds_case_id = :caseId"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("FDS case not found: $caseId")

    private fun findForUpdate(caseId: String): FdsCaseDto =
        jdbc.queryForObject(
            fdsSql("WHERE fds_case_id = :caseId FOR UPDATE"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("FDS case not found: $caseId")

    private fun fdsSql(suffix: String): String =
        """
        SELECT fds_case_id, journey_id, transfer_reference_id, customer_id, status, risk_score,
               alerts_json::text AS alerts_json, owner_id, approval_id, from_account_id,
               to_account_id, amount_minor, transfer_idempotency_key, requested_by,
               business_date, transfer_status, temporal_workflow_id, temporal_run_id,
               created_at, updated_at
        FROM fds_cases
        $suffix
        """.trimIndent()

    private fun mapCase(rs: ResultSet, rowNum: Int): FdsCaseDto =
        FdsCaseDto(
            caseId = rs.getString("fds_case_id"),
            journeyId = rs.getString("journey_id"),
            transferReferenceId = rs.getString("transfer_reference_id"),
            customerId = rs.getString("customer_id"),
            status = rs.getString("status"),
            riskScore = rs.getInt("risk_score"),
            alerts = readAlerts(rs.getString("alerts_json")),
            owner = rs.getString("owner_id"),
            approvalId = rs.getString("approval_id"),
            fromAccountId = rs.getString("from_account_id"),
            toAccountId = rs.getString("to_account_id"),
            amountMinor = nullableLong(rs, "amount_minor"),
            transferIdempotencyKey = rs.getString("transfer_idempotency_key"),
            requestedBy = rs.getString("requested_by"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            transferStatus = rs.getString("transfer_status"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            temporalWorkflow = temporalReference(rs)
        )

    private fun readAlerts(alertsJson: String): List<FdsAlert> =
        objectMapper.readValue(
            alertsJson,
            objectMapper.typeFactory.constructCollectionType(List::class.java, FdsAlert::class.java)
        )

    private fun nullableLong(rs: ResultSet, column: String): Long? {
        val value = rs.getLong(column)
        return if (rs.wasNull()) null else value
    }

    private fun requireField(value: String?, field: String): String =
        value ?: throw WorkflowErrors.validation("$field is required")

    private fun releaseIdempotencyKey(caseId: String): String =
        "FDS-RELEASE-$caseId"

    private fun temporalReference(rs: ResultSet): TemporalWorkflowReference? {
        val workflowId = rs.getString("temporal_workflow_id") ?: return null
        return TemporalWorkflowReference(
            workflowId = workflowId,
            runId = rs.getString("temporal_run_id")
        )
    }

    private companion object {
        const val SERIALIZABLE_FDS_DECISION_MAX_ATTEMPTS = 5
    }
}
