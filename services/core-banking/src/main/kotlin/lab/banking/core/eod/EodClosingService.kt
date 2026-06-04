package lab.banking.core.eod

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.eventing.CreateOutboxEventCommand
import lab.banking.core.eventing.DurableOutboxService
import lab.banking.core.product.DepositProductService
import lab.banking.core.product.FeePolicyService
import lab.banking.core.product.FeePostingBatchCommand
import lab.banking.core.product.InterestAccrualRunCommand
import lab.banking.core.product.InterestPostingBatchCommand
import lab.banking.core.reconciliation.ReconciliationDailyClosingCommand
import lab.banking.core.reconciliation.ReconciliationItemDto
import lab.banking.core.reconciliation.ReconciliationOpsService
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class EodClosingService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val auditEvents: AuditEventAppender,
    private val outbox: DurableOutboxService,
    private val depositProductService: DepositProductService,
    private val feePolicyService: FeePolicyService,
    private val reconciliationOpsService: ReconciliationOpsService,
    private val transactionManager: PlatformTransactionManager
) {
    fun requestClose(command: EodCloseCommand): EodCloseRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        requireRole(command.requestedByRole, EOD_REQUEST_ROLES, "actor role cannot request EOD closing")
        requireReason(command.reason, "EOD_CLOSING requires a business reason")
        val businessReferenceId = businessReferenceId(command.businessDate)
        val monitor = monitor(command.businessDate)
        if (monitor.status == "CLOSED") {
            return EodCloseRequestResponse(approval = existingApproval(businessReferenceId), monitor = monitor, replayed = true)
        }
        existingApproval(businessReferenceId)?.let {
            return EodCloseRequestResponse(approval = it, monitor = monitor, replayed = true)
        }
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.EOD_CLOSING,
                businessReferenceId = businessReferenceId,
                requestedBy = command.requestedBy,
                requestedByRole = command.requestedByRole,
                requestReason = command.reason,
                beforeSnapshot = mapOf(
                    "businessDate" to command.businessDate.toString(),
                    "status" to monitor.status,
                    "syntheticOnly" to true
                ),
                afterSnapshot = mapOf(
                    "businessDate" to command.businessDate.toString(),
                    "idempotencyKey" to (command.idempotencyKey ?: "EOD-${command.businessDate}"),
                    "feePolicyId" to command.feePolicyId,
                    "externalMode" to (command.externalMode ?: "MATCHED"),
                    "requestedBy" to command.requestedBy,
                    "requestedByRole" to command.requestedByRole,
                    "reason" to command.reason,
                    "syntheticOnly" to true
                ),
                screenId = "OPS-101"
            )
        )
        auditEvents.append(
            eventType = "COMMAND_REQUESTED",
            actorType = "STAFF",
            actorId = command.requestedBy,
            actorRole = command.requestedByRole,
            screenId = "OPS-101",
            businessReferenceId = businessReferenceId,
            reason = command.reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessDate" to command.businessDate.toString(),
                "syntheticOnly" to true
            )
        )
        return EodCloseRequestResponse(approval = approval, monitor = monitor(command.businessDate), replayed = false)
    }

    fun applyApprovedClose(approval: OperatorApproval): EodClosingExecutionResponse {
        if (approval.businessType != ApprovalBusinessTypes.EOD_CLOSING) {
            throw WorkflowErrors.stateViolation("approval is not an EOD closing approval")
        }
        val snapshot = approval.afterSnapshot ?: throw WorkflowErrors.stateViolation("EOD approval is missing command snapshot")
        val businessDate = LocalDate.parse(snapshot["businessDate"]?.toString() ?: approval.businessReferenceId.removePrefix("EOD-"))
        if (monitor(businessDate).status == "CLOSED") {
            return EodClosingExecutionResponse(monitor = monitor(businessDate), replayed = true)
        }
        val requestedBy = snapshot["requestedBy"]?.toString() ?: approval.requestedBy
        val requestedByRole = snapshot["requestedByRole"]?.toString() ?: "OPS_OPERATOR"
        val reason = snapshot["reason"]?.toString()?.takeIf { it.isNotBlank() } ?: approval.requestReason
        val feePolicyId = snapshot["feePolicyId"]?.toString()?.takeIf { it.isNotBlank() }
        val externalMode = snapshot["externalMode"]?.toString()?.takeIf { it.isNotBlank() } ?: "MATCHED"
        val idempotencyKey = snapshot["idempotencyKey"]?.toString()?.takeIf { it.isNotBlank() } ?: "EOD-$businessDate"

        initializeSteps(businessDate)
        runStep(businessDate, EodClosingStep.INTEREST_ACCRUAL, idempotencyKey) {
            val response = depositProductService.runInterestAccrual(
                InterestAccrualRunCommand(
                    accrualDate = businessDate,
                    requestedBy = requestedBy,
                    actorRole = requestedByRole,
                    reason = reason
                )
            )
            mapOf(
                "accountCount" to response.items.size,
                "totalInterestMinor" to response.totalInterestMinor,
                "syntheticOnly" to true
            )
        }
        runStep(businessDate, EodClosingStep.INTEREST_POSTING, idempotencyKey) {
            runCatching {
                val response = depositProductService.postInterestBatch(
                    InterestPostingBatchCommand(
                        businessDate = businessDate,
                        requestedBy = requestedBy,
                        actorRole = requestedByRole,
                        reason = reason,
                        idempotencyKey = "$idempotencyKey-INTEREST"
                    )
                )
                mapOf(
                    "batchId" to response.item.batchId,
                    "ledgerTransactionId" to response.item.ledgerTransactionId,
                    "totalInterestMinor" to response.item.totalInterestMinor,
                    "replayed" to response.replayed,
                    "syntheticOnly" to true
                )
            }.getOrElse { error ->
                if (error is RuntimeException && error.message.orEmpty().contains("no calculated interest accruals")) {
                    mapOf("skipped" to true, "reason" to "no calculated interest accruals", "syntheticOnly" to true)
                } else {
                    throw error
                }
            }
        }
        runStep(businessDate, EodClosingStep.FEE_POSTING, idempotencyKey) {
            val policyId = feePolicyId ?: firstActiveFeePolicyId()
            if (policyId == null) {
                mapOf("skipped" to true, "reason" to "no active fee policy", "syntheticOnly" to true)
            } else {
                runCatching {
                    val response = feePolicyService.postFeeBatch(
                        FeePostingBatchCommand(
                            policyId = policyId,
                            businessDate = businessDate,
                            requestedBy = requestedBy,
                            actorRole = requestedByRole,
                            reason = reason,
                            idempotencyKey = "$idempotencyKey-FEE"
                        )
                    )
                    mapOf(
                        "batchId" to response.item.batchId,
                        "ledgerTransactionId" to response.item.ledgerTransactionId,
                        "totalFeeMinor" to response.item.totalFeeMinor,
                        "replayed" to response.replayed,
                        "syntheticOnly" to true
                    )
                }.getOrElse { error ->
                    if (error is RuntimeException && error.message.orEmpty().contains("no eligible accounts")) {
                        mapOf("skipped" to true, "reason" to "no eligible accounts", "syntheticOnly" to true)
                    } else {
                        throw error
                    }
                }
            }
        }
        runStep(businessDate, EodClosingStep.RECONCILIATION, idempotencyKey) {
            val response = reconciliationOpsService.closeBusinessDay(
                ReconciliationDailyClosingCommand(
                    businessDate = businessDate,
                    idempotencyKey = idempotencyKey,
                    requestedBy = requestedBy,
                    actorRole = requestedByRole,
                    externalMode = externalMode
                )
            )
            mapOf(
                "status" to response.item.status,
                "internalEntryCount" to response.item.internalEntryCount,
                "externalEntryCount" to response.item.externalEntryCount,
                "unmatchedItemCount" to response.item.unmatchedItemCount,
                "replayed" to response.replayed,
                "syntheticOnly" to true
            )
        }
        runStep(businessDate, EodClosingStep.DAILY_CLOSING, idempotencyKey) {
            val dailyClosing = dailyClosingRow(businessDate)
            mapOf(
                "status" to dailyClosing?.status,
                "ledgerTotalHash" to dailyClosing?.ledgerTotalHash,
                "syntheticOnly" to true
            )
        }
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "STAFF",
            actorId = approval.approvedBy ?: "UNKNOWN",
            actorRole = "OPS_MANAGER",
            screenId = "OPS-101",
            businessReferenceId = businessReferenceId(businessDate),
            reason = reason,
            payload = mapOf(
                "approvalId" to approval.approvalId,
                "businessType" to ApprovalBusinessTypes.EOD_CLOSING,
                "businessDate" to businessDate.toString(),
                "syntheticOnly" to true,
                "ledgerSourceRowsMutated" to false
            )
        )
        return EodClosingExecutionResponse(monitor = monitor(businessDate), replayed = false)
    }

    fun monitor(businessDate: LocalDate): EodClosingMonitorDto {
        initializeSteps(businessDate)
        val dailyClosing = dailyClosingRow(businessDate)
        val steps = stepRows(businessDate)
        val status = when {
            dailyClosing?.status == "CLOSED" && steps.all { it.status == "COMPLETED" || it.status == "SKIPPED" } -> "CLOSED"
            steps.any { it.status == "FAILED" } -> "FAILED"
            steps.any { it.status == "RUNNING" || it.status == "COMPLETED" || it.status == "SKIPPED" } -> "CLOSING"
            else -> dailyClosing?.status ?: "OPEN"
        }
        return EodClosingMonitorDto(
            businessDate = businessDate,
            status = status,
            dailyClosingStatus = dailyClosing?.status,
            ledgerTotalHash = dailyClosing?.ledgerTotalHash,
            steps = steps,
            reconciliationItems = reconciliationItems(businessDate)
        )
    }

    private fun runStep(
        businessDate: LocalDate,
        step: EodClosingStep,
        idempotencyKey: String,
        operation: () -> Map<String, Any?>
    ) {
        val existing = stepRow(businessDate, step)
        if (existing?.status == "COMPLETED" || existing?.status == "SKIPPED") {
            return
        }
        markStepRunning(businessDate, step)
        try {
            val result = newTransaction().execute<Map<String, Any?>> {
                operation()
            } ?: emptyMap()
            val status = if (result["skipped"] == true) "SKIPPED" else "COMPLETED"
            markStepFinished(businessDate, step, status, result)
            newTransaction().executeWithoutResult {
                outbox.enqueue(
                    CreateOutboxEventCommand(
                        aggregateType = "EndOfDayClosing",
                        aggregateId = businessDate.toString(),
                        eventType = "EodStep$status",
                        idempotencyKey = "$idempotencyKey-${step.name}",
                        payload = result + mapOf("businessDate" to businessDate.toString(), "step" to step.name)
                    )
                )
                auditEvents.append(
                    eventType = "EOD_STEP_$status",
                    actorType = "STAFF",
                    actorId = "SYSTEM",
                    actorRole = "OPS_OPERATOR",
                    screenId = "OPS-101",
                    businessReferenceId = businessReferenceId(businessDate),
                    reason = null,
                    payload = result + mapOf("businessDate" to businessDate.toString(), "step" to step.name)
                )
            }
        } catch (error: RuntimeException) {
            markStepFinished(
                businessDate = businessDate,
                step = step,
                status = "FAILED",
                result = mapOf("message" to error.message.orEmpty(), "syntheticOnly" to true)
            )
            throw error
        }
    }

    private fun initializeSteps(businessDate: LocalDate) {
        EodClosingStep.entries.forEach { step ->
            jdbc.update(
                """
                INSERT INTO eod_closing_steps (business_date, step, status)
                VALUES (:businessDate, :step, 'PENDING')
                ON CONFLICT (business_date, step) DO NOTHING
                """.trimIndent(),
                mapOf("businessDate" to businessDate, "step" to step.name)
            )
        }
    }

    private fun markStepRunning(businessDate: LocalDate, step: EodClosingStep) {
        newTransaction().executeWithoutResult {
            jdbc.update(
                """
                UPDATE eod_closing_steps
                SET status = 'RUNNING',
                    started_at = COALESCE(started_at, now()),
                    version = version + 1
                WHERE business_date = :businessDate
                  AND step = :step
                  AND status IN ('PENDING', 'RUNNING', 'FAILED')
                """.trimIndent(),
                mapOf("businessDate" to businessDate, "step" to step.name)
            )
        }
    }

    private fun markStepFinished(businessDate: LocalDate, step: EodClosingStep, status: String, result: Map<String, Any?>) {
        newTransaction().executeWithoutResult {
            jdbc.update(
                """
                UPDATE eod_closing_steps
                SET status = :status,
                    finished_at = now(),
                    result_json = CAST(:resultJson AS jsonb),
                    version = version + 1
                WHERE business_date = :businessDate
                  AND step = :step
                """.trimIndent(),
                mapOf(
                    "businessDate" to businessDate,
                    "step" to step.name,
                    "status" to status,
                    "resultJson" to objectMapper.writeValueAsString(result)
                )
            )
        }
    }

    private fun newTransaction(): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    private fun existingApproval(businessReferenceId: String): OperatorApproval? =
        jdbc.query(
            """
            SELECT approval_id
            FROM operator_approvals
            WHERE business_type = :businessType
              AND business_reference_id = :businessReferenceId
            ORDER BY requested_at DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("businessType" to ApprovalBusinessTypes.EOD_CLOSING, "businessReferenceId" to businessReferenceId)
        ) { rs, _ -> approvals.approval(rs.getString("approval_id")) }
            .firstOrNull()

    private fun dailyClosingRow(businessDate: LocalDate): DailyClosingRow? =
        jdbc.query(
            """
            SELECT business_date, status, ledger_total_hash
            FROM daily_closings
            WHERE business_date = :businessDate
            """.trimIndent(),
            mapOf("businessDate" to businessDate)
        ) { rs, _ ->
            DailyClosingRow(
                businessDate = rs.getObject("business_date", LocalDate::class.java),
                status = rs.getString("status"),
                ledgerTotalHash = rs.getString("ledger_total_hash")
            )
        }.firstOrNull()

    private fun stepRows(businessDate: LocalDate): List<EodClosingStepDto> =
        jdbc.query(
            """
            SELECT business_date, step, status, started_at, finished_at, result_json
            FROM eod_closing_steps
            WHERE business_date = :businessDate
            ORDER BY CASE step
              WHEN 'INTEREST_ACCRUAL' THEN 1
              WHEN 'INTEREST_POSTING' THEN 2
              WHEN 'FEE_POSTING' THEN 3
              WHEN 'RECONCILIATION' THEN 4
              WHEN 'DAILY_CLOSING' THEN 5
              ELSE 99
            END
            """.trimIndent(),
            mapOf("businessDate" to businessDate),
            this::mapStep
        )

    private fun stepRow(businessDate: LocalDate, step: EodClosingStep): EodClosingStepDto? =
        jdbc.query(
            """
            SELECT business_date, step, status, started_at, finished_at, result_json
            FROM eod_closing_steps
            WHERE business_date = :businessDate
              AND step = :step
            """.trimIndent(),
            mapOf("businessDate" to businessDate, "step" to step.name),
            this::mapStep
        ).firstOrNull()

    private fun mapStep(rs: ResultSet, rowNum: Int): EodClosingStepDto =
        EodClosingStepDto(
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            step = EodClosingStep.valueOf(rs.getString("step")),
            status = rs.getString("status"),
            startedAt = rs.getObject("started_at", java.time.OffsetDateTime::class.java),
            finishedAt = rs.getObject("finished_at", java.time.OffsetDateTime::class.java),
            result = readMap(rs.getString("result_json"))
        )

    private fun reconciliationItems(businessDate: LocalDate): List<ReconciliationItemDto> =
        reconciliationOpsService.list()
            .filter { it.businessDate == businessDate }

    private fun firstActiveFeePolicyId(): String? =
        jdbc.query(
            """
            SELECT policy_id
            FROM fee_policies
            WHERE status = 'ACTIVE'
            ORDER BY policy_id
            LIMIT 1
            """.trimIndent(),
            emptyMap<String, Any?>()
        ) { rs, _ -> rs.getString("policy_id") }.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun readMap(value: String?): Map<String, Any?> =
        if (value.isNullOrBlank()) {
            emptyMap()
        } else {
            objectMapper.readValue(value, Map::class.java) as Map<String, Any?>
        }

    private fun businessReferenceId(businessDate: LocalDate): String =
        "EOD-$businessDate"

    private fun requireReason(reason: String?, message: String) {
        if (reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired(message)
        }
    }

    private fun requireRole(role: String, allowedRoles: Set<String>, message: String) {
        if (role !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation(message)
        }
    }

    private data class DailyClosingRow(
        val businessDate: LocalDate,
        val status: String,
        val ledgerTotalHash: String?
    )

    private companion object {
        val EOD_REQUEST_ROLES = setOf("OPS_OPERATOR", "OPS_MANAGER")
    }
}
