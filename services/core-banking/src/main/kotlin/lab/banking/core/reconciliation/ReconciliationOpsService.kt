package lab.banking.core.reconciliation

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.ledger.application.AdjustmentCommand
import lab.banking.core.ledger.application.DailyClosingCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.domain.LedgerCommandResult
import lab.banking.core.ledger.domain.PostingDirection
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
class ReconciliationOpsService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender,
    private val approvals: PersistentApprovalService,
    private val ledgerCommandService: LedgerCommandService,
    private val transactionManager: PlatformTransactionManager
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun closeBusinessDay(command: ReconciliationDailyClosingCommand): ReconciliationClosingResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.actorRole)
        val businessDate = command.businessDate ?: LocalDate.now()
        val requestedBy = command.requestedBy ?: "ops01"
        val idempotencyKey = command.idempotencyKey ?: "EOD-$businessDate"
        val internalEntries = internalTransferEntries(businessDate)
        val externalFile = simulateExternalFile(
            businessDate = businessDate,
            mode = command.externalMode ?: "MISMATCH",
            internalEntries = internalEntries
        )
        val ledgerInvariantValid = unbalancedTransactionCount() == 0
        if (!ledgerInvariantValid) {
            throw WorkflowErrors.stateViolation("ledger invariant check failed before daily closing")
        }

        val closing = ledgerCommandService.closeBusinessDay(
            DailyClosingCommand(
                businessDate = businessDate,
                idempotencyKey = idempotencyKey,
                requestedBy = requestedBy
            )
        )
        val items = if (closing.replayed) {
            listByBusinessDate(businessDate)
        } else {
            createMismatchItems(
                businessDate = businessDate,
                owner = requestedBy,
                feedFileId = externalFile.fileId,
                internalEntries = internalEntries,
                externalEntries = externalFile.entries
            )
        }
        appendAudit(
            eventType = "BATCH_STARTED",
            actorId = requestedBy,
            actorRole = command.actorRole ?: "OPS_OPERATOR",
            screenId = "OPS-101",
            businessReferenceId = "EOD-$businessDate",
            accountId = null,
            reason = null,
            payload = mapOf(
                "businessDate" to businessDate.toString(),
                "status" to if (items.isEmpty()) "MATCHED" else "UNMATCHED",
                "unmatchedItemCount" to items.size,
                "syntheticOnly" to true
            )
        )
        return ReconciliationClosingResponse(
            item = ReconciliationClosingDto(
                closingId = "EOD-$businessDate",
                businessDate = businessDate,
                status = if (items.isEmpty()) "MATCHED" else "UNMATCHED",
                ledgerInvariantValid = true,
                internalEntryCount = internalEntries.size,
                externalEntryCount = externalFile.entries.size,
                unmatchedItemCount = items.size,
                internalTotalMinor = internalEntries.sumOf { it.amountMinor },
                externalTotalMinor = externalFile.entries.sumOf { it.amountMinor }
            ),
            externalFile = externalFile,
            reconciliationItems = items,
            replayed = closing.replayed
        )
    }

    @Transactional(readOnly = true)
    fun list(): List<ReconciliationItemDto> =
        jdbc.query(
            itemSql("ORDER BY created_at, reconciliation_item_id"),
            emptyMap<String, Any?>(),
            this::mapItem
        )

    @Transactional(readOnly = true)
    fun find(itemId: String): ReconciliationItemDto =
        findForRead(itemId)

    fun requestAdjustment(itemId: String, command: ReconciliationAdjustmentCommand): ReconciliationAdjustmentRequestResponse =
        runSerializableAdjustmentRequest {
            requestAdjustmentInTransaction(itemId, command)
        }

    private fun requestAdjustmentInTransaction(itemId: String, command: ReconciliationAdjustmentCommand): ReconciliationAdjustmentRequestResponse {
        BankingLabAuthContext.requireActor(command.requestedBy, command.requestedByRole)
        if (command.reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("RECONCILIATION_ADJUSTMENT requires a business reason")
        }
        val item = findForUpdate(itemId)
        if (item.status != "OPEN" && item.status != "INVESTIGATING") {
            throw WorkflowErrors.stateViolation("reconciliation item is not open for adjustment: ${item.status}")
        }
        val amountMinor = command.amountMinor ?: kotlin.math.abs(item.amountMinor)
        if (amountMinor <= 0) {
            throw WorkflowErrors.validation("amountMinor must be a positive integer minor-unit value")
        }
        val requestedBy = command.requestedBy ?: "ops01"
        val accountId = command.accountId ?: "ACC-SYN-001-001"
        val direction = command.direction ?: PostingDirection.CREDIT
        val adjustmentBusinessDate = command.businessDate ?: item.businessDate.plusDays(1)
        val idempotencyKey = command.idempotencyKey ?: "REC-ADJ-$itemId"
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT,
                businessReferenceId = itemId,
                requestedBy = requestedBy,
                requestedByRole = command.requestedByRole ?: "OPS_OPERATOR",
                requestReason = command.reason,
                beforeSnapshot = mapOf(
                    "status" to item.status,
                    "amountMinor" to item.amountMinor,
                    "internalReferenceId" to item.internalReferenceId,
                    "externalReferenceId" to item.externalReferenceId
                ),
                afterSnapshot = mapOf(
                    "accountId" to accountId,
                    "direction" to direction.name,
                    "amountMinor" to amountMinor,
                    "businessDate" to adjustmentBusinessDate.toString(),
                    "idempotencyKey" to idempotencyKey
                ),
                screenId = "OPS-201"
            )
        )
        jdbc.update(
            """
            INSERT INTO reconciliation_adjustment_requests (
              reconciliation_adjustment_request_id, reconciliation_item_id, approval_id,
              account_id, direction, amount_minor, business_date, idempotency_key,
              reason, requested_by, status
            )
            VALUES (
              :requestId, :itemId, :approvalId, :accountId, :direction, :amountMinor,
              :businessDate, :idempotencyKey, :reason, :requestedBy, 'REQUESTED'
            )
            """.trimIndent(),
            mapOf(
                "requestId" to "RAR-${UUID.randomUUID().toString().uppercase()}",
                "itemId" to itemId,
                "approvalId" to approval.approvalId,
                "accountId" to accountId,
                "direction" to direction.name,
                "amountMinor" to amountMinor,
                "businessDate" to adjustmentBusinessDate,
                "idempotencyKey" to idempotencyKey,
                "reason" to command.reason,
                "requestedBy" to requestedBy
            )
        )
        jdbc.update(
            """
            UPDATE reconciliation_items
            SET status = 'ADJUSTMENT_REQUESTED',
                approval_id = :approvalId
            WHERE reconciliation_item_id = :itemId
            """.trimIndent(),
            mapOf("itemId" to itemId, "approvalId" to approval.approvalId)
        )
        return ReconciliationAdjustmentRequestResponse(item = findForRead(itemId), approval = approval)
    }

    private fun <T> runSerializableAdjustmentRequest(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable reconciliation adjustment request returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_RECONCILIATION_ADJUSTMENT_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
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
    fun applyApprovedAdjustment(approval: OperatorApproval): ReconciliationAdjustmentExecutionResponse {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessType != ApprovalBusinessTypes.RECONCILIATION_ADJUSTMENT) {
            throw WorkflowErrors.stateViolation("approval is not a reconciliation adjustment approval")
        }
        val item = findForUpdate(approval.businessReferenceId)
        if (item.status != "ADJUSTMENT_REQUESTED") {
            throw WorkflowErrors.stateViolation("reconciliation item is not waiting adjustment approval: ${item.status}")
        }
        val request = adjustmentRequestForUpdate(item.itemId, approval.approvalId)
        if (request.status != "REQUESTED") {
            throw WorkflowErrors.stateViolation("reconciliation adjustment request is not requested: ${request.status}")
        }
        val result = ledgerCommandService.adjustment(
            AdjustmentCommand(
                accountId = request.accountId,
                direction = request.direction,
                amountMinor = request.amountMinor,
                idempotencyKey = request.idempotencyKey,
                requestedBy = request.requestedBy,
                requestedChannel = "OPS_RECONCILIATION",
                businessDate = request.businessDate,
                reason = request.reason,
                currency = item.currency,
                businessReferenceId = item.itemId,
                approvalId = approval.approvalId
            )
        )
        markAdjustmentPosted(request.requestId, item.itemId, result)
        return ReconciliationAdjustmentExecutionResponse(item = findForRead(item.itemId), ledgerTransaction = result)
    }

    private fun createMismatchItems(
        businessDate: LocalDate,
        owner: String,
        feedFileId: String,
        internalEntries: List<ReconciliationExternalEntryDto>,
        externalEntries: List<ReconciliationExternalEntryDto>
    ): List<ReconciliationItemDto> {
        val externalByReference = externalEntries.groupBy { it.referenceId }
        val internalReferences = internalEntries.map { it.referenceId }.toSet()
        val createdIds = mutableListOf<String>()

        for (internal in internalEntries) {
            val externalGroup = externalByReference[internal.referenceId].orEmpty()
            if (externalGroup.isEmpty()) {
                createdIds += insertMismatch(
                    businessDate = businessDate,
                    owner = owner,
                    feedFileId = feedFileId,
                    mismatchType = "MISSING_EXTERNAL",
                    internalReferenceId = internal.referenceId,
                    externalReferenceId = null,
                    amountMinor = internal.amountMinor,
                    internalAmountMinor = internal.amountMinor,
                    externalAmountMinor = null,
                    externalStatus = null,
                    detectedReason = "Internal posted transfer is missing from synthetic external feed"
                )
            } else if (externalGroup.size > 1) {
                val externalTotal = externalGroup.sumOf { it.amountMinor }
                createdIds += insertMismatch(
                    businessDate = businessDate,
                    owner = owner,
                    feedFileId = feedFileId,
                    mismatchType = "DUPLICATE_EXTERNAL",
                    internalReferenceId = internal.referenceId,
                    externalReferenceId = internal.referenceId,
                    amountMinor = kotlin.math.abs(externalTotal - internal.amountMinor).takeIf { it > 0 } ?: internal.amountMinor,
                    internalAmountMinor = internal.amountMinor,
                    externalAmountMinor = externalTotal,
                    externalStatus = "DUPLICATE",
                    detectedReason = "Synthetic external feed contains duplicate entries for the internal transfer reference"
                )
            } else {
                val external = externalGroup.first()
                if (external.businessDate != businessDate || external.status == "STALE") {
                    createdIds += insertMismatch(
                        businessDate = businessDate,
                        owner = owner,
                        feedFileId = feedFileId,
                        mismatchType = "STALE_EXTERNAL",
                        internalReferenceId = internal.referenceId,
                        externalReferenceId = external.referenceId,
                        amountMinor = internal.amountMinor,
                        internalAmountMinor = internal.amountMinor,
                        externalAmountMinor = external.amountMinor,
                        externalStatus = external.status,
                        detectedReason = "Synthetic external feed entry is stale for the closing business date"
                    )
                } else if (external.amountMinor != internal.amountMinor) {
                    createdIds += insertMismatch(
                        businessDate = businessDate,
                        owner = owner,
                        feedFileId = feedFileId,
                        mismatchType = "AMOUNT_MISMATCH",
                        internalReferenceId = internal.referenceId,
                        externalReferenceId = external.referenceId,
                        amountMinor = kotlin.math.abs(external.amountMinor - internal.amountMinor),
                        internalAmountMinor = internal.amountMinor,
                        externalAmountMinor = external.amountMinor,
                        externalStatus = external.status,
                        detectedReason = "Synthetic external feed amount differs from the posted internal transfer"
                    )
                } else if (external.status != "SETTLED") {
                    createdIds += insertMismatch(
                        businessDate = businessDate,
                        owner = owner,
                        feedFileId = feedFileId,
                        mismatchType = "STATUS_MISMATCH",
                        internalReferenceId = internal.referenceId,
                        externalReferenceId = external.referenceId,
                        amountMinor = internal.amountMinor,
                        internalAmountMinor = internal.amountMinor,
                        externalAmountMinor = external.amountMinor,
                        externalStatus = external.status,
                        detectedReason = "Synthetic external feed status is not settled"
                    )
                }
            }
        }
        for (external in externalEntries) {
            if (!internalReferences.contains(external.referenceId)) {
                createdIds += insertMismatch(
                    businessDate = businessDate,
                    owner = owner,
                    feedFileId = feedFileId,
                    mismatchType = "UNEXPECTED_EXTERNAL",
                    internalReferenceId = null,
                    externalReferenceId = external.referenceId,
                    amountMinor = external.amountMinor,
                    internalAmountMinor = null,
                    externalAmountMinor = external.amountMinor,
                    externalStatus = external.status,
                    detectedReason = "Synthetic external feed entry has no matching internal posted transfer"
                )
            }
        }
        return createdIds.map(::findForRead)
    }

    private fun insertMismatch(
        businessDate: LocalDate,
        owner: String,
        feedFileId: String,
        mismatchType: String,
        internalReferenceId: String?,
        externalReferenceId: String?,
        amountMinor: Long,
        internalAmountMinor: Long?,
        externalAmountMinor: Long?,
        externalStatus: String?,
        detectedReason: String
    ): String {
        val itemId = "REC-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO reconciliation_items (
              reconciliation_item_id, business_date, source_system, internal_reference_id,
              external_reference_id, mismatch_type, amount_minor, internal_amount_minor,
              external_amount_minor, external_status, feed_file_id, detected_reason,
              currency, status, owner_id
            )
            VALUES (
              :itemId, :businessDate, 'EOD_EXTERNAL_SIM', :internalReferenceId,
              :externalReferenceId, :mismatchType, :amountMinor, :internalAmountMinor,
              :externalAmountMinor, :externalStatus, :feedFileId, :detectedReason,
              'KRW', 'OPEN', :owner
            )
            """.trimIndent(),
            mapOf(
                "itemId" to itemId,
                "businessDate" to businessDate,
                "internalReferenceId" to internalReferenceId,
                "externalReferenceId" to externalReferenceId,
                "mismatchType" to mismatchType,
                "amountMinor" to amountMinor,
                "internalAmountMinor" to internalAmountMinor,
                "externalAmountMinor" to externalAmountMinor,
                "externalStatus" to externalStatus,
                "feedFileId" to feedFileId,
                "detectedReason" to detectedReason,
                "owner" to owner
            )
        )
        return itemId
    }

    private fun markAdjustmentPosted(requestId: String, itemId: String, result: LedgerCommandResult) {
        jdbc.update(
            """
            UPDATE reconciliation_adjustment_requests
            SET status = 'POSTED',
                ledger_transaction_id = :ledgerTransactionId,
                updated_at = now()
            WHERE reconciliation_adjustment_request_id = :requestId
            """.trimIndent(),
            mapOf("requestId" to requestId, "ledgerTransactionId" to result.value.id)
        )
        jdbc.update(
            """
            UPDATE reconciliation_items
            SET status = 'ADJUSTED',
                approval_id = NULL
            WHERE reconciliation_item_id = :itemId
            """.trimIndent(),
            mapOf("itemId" to itemId)
        )
    }

    private fun listByBusinessDate(businessDate: LocalDate): List<ReconciliationItemDto> =
        jdbc.query(
            itemSql("WHERE business_date = :businessDate ORDER BY created_at, reconciliation_item_id"),
            mapOf("businessDate" to businessDate),
            this::mapItem
        )

    private fun findForRead(itemId: String): ReconciliationItemDto =
        jdbc.queryForObject(
            itemSql("WHERE reconciliation_item_id = :itemId"),
            mapOf("itemId" to itemId),
            this::mapItem
        ) ?: throw WorkflowErrors.notFound("reconciliation item not found: $itemId")

    private fun findForUpdate(itemId: String): ReconciliationItemDto =
        jdbc.queryForObject(
            itemSql("WHERE reconciliation_item_id = :itemId FOR UPDATE"),
            mapOf("itemId" to itemId),
            this::mapItem
        ) ?: throw WorkflowErrors.notFound("reconciliation item not found: $itemId")

    private fun itemSql(suffix: String): String =
        """
        SELECT reconciliation_item_id, business_date, source_system, internal_reference_id,
               external_reference_id, mismatch_type, amount_minor, internal_amount_minor,
               external_amount_minor, external_status, feed_file_id, detected_reason,
               currency, status, owner_id, approval_id, created_at
        FROM reconciliation_items
        $suffix
        """.trimIndent()

    private fun mapItem(rs: ResultSet, rowNum: Int): ReconciliationItemDto {
        val itemId = rs.getString("reconciliation_item_id")
        val request = latestAdjustmentRequest(itemId)
        return ReconciliationItemDto(
            itemId = itemId,
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            sourceSystem = rs.getString("source_system"),
            internalReferenceId = rs.getString("internal_reference_id"),
            externalReferenceId = rs.getString("external_reference_id"),
            mismatchType = rs.getString("mismatch_type"),
            amountMinor = rs.getLong("amount_minor"),
            internalAmountMinor = nullableLong(rs, "internal_amount_minor"),
            externalAmountMinor = nullableLong(rs, "external_amount_minor"),
            externalStatus = rs.getString("external_status"),
            feedFileId = rs.getString("feed_file_id"),
            detectedReason = rs.getString("detected_reason"),
            currency = rs.getString("currency"),
            status = rs.getString("status"),
            owner = rs.getString("owner_id"),
            approvalId = rs.getString("approval_id"),
            adjustmentTransactionId = request?.ledgerTransactionId,
            adjustmentRequest = request,
            createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java)
        )
    }

    private fun nullableLong(rs: ResultSet, column: String): Long? {
        val value = rs.getLong(column)
        return if (rs.wasNull()) null else value
    }

    private fun latestAdjustmentRequest(itemId: String): ReconciliationAdjustmentRequestDto? =
        jdbc.query(
            """
            SELECT reconciliation_adjustment_request_id, approval_id, account_id, direction,
                   amount_minor, business_date, idempotency_key, reason, requested_by,
                   status, ledger_transaction_id, temporal_workflow_id, temporal_run_id,
                   created_at, updated_at
            FROM reconciliation_adjustment_requests
            WHERE reconciliation_item_id = :itemId
            ORDER BY created_at DESC, reconciliation_adjustment_request_id DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("itemId" to itemId),
            this::mapAdjustmentRequest
        ).firstOrNull()

    private fun adjustmentRequestForUpdate(itemId: String, approvalId: String): ReconciliationAdjustmentRequestDto =
        jdbc.query(
            """
            SELECT reconciliation_adjustment_request_id, approval_id, account_id, direction,
                   amount_minor, business_date, idempotency_key, reason, requested_by,
                   status, ledger_transaction_id, temporal_workflow_id, temporal_run_id,
                   created_at, updated_at
            FROM reconciliation_adjustment_requests
            WHERE reconciliation_item_id = :itemId
              AND approval_id = :approvalId
            FOR UPDATE
            """.trimIndent(),
            mapOf("itemId" to itemId, "approvalId" to approvalId),
            this::mapAdjustmentRequest
        ).firstOrNull() ?: throw WorkflowErrors.stateViolation("reconciliation adjustment request is missing")

    private fun mapAdjustmentRequest(rs: ResultSet, rowNum: Int): ReconciliationAdjustmentRequestDto =
        ReconciliationAdjustmentRequestDto(
            requestId = rs.getString("reconciliation_adjustment_request_id"),
            approvalId = rs.getString("approval_id"),
            accountId = rs.getString("account_id"),
            direction = PostingDirection.valueOf(rs.getString("direction")),
            amountMinor = rs.getLong("amount_minor"),
            businessDate = rs.getObject("business_date", LocalDate::class.java),
            idempotencyKey = rs.getString("idempotency_key"),
            reason = rs.getString("reason"),
            requestedBy = rs.getString("requested_by"),
            status = rs.getString("status"),
            ledgerTransactionId = rs.getString("ledger_transaction_id"),
            createdAt = rs.getObject("created_at", java.time.OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", java.time.OffsetDateTime::class.java),
            temporalWorkflow = temporalReference(rs)
        )

    private fun temporalReference(rs: ResultSet): TemporalWorkflowReference? {
        val workflowId = rs.getString("temporal_workflow_id") ?: return null
        return TemporalWorkflowReference(
            workflowId = workflowId,
            runId = rs.getString("temporal_run_id")
        )
    }

    private fun internalTransferEntries(businessDate: LocalDate): List<ReconciliationExternalEntryDto> =
        jdbc.query(
            """
            SELECT lt.ledger_transaction_id, lt.business_date, lp.amount_minor
            FROM ledger_transactions lt
            JOIN ledger_postings lp
              ON lp.ledger_transaction_id = lt.ledger_transaction_id
             AND lp.direction = 'DEBIT'
            WHERE lt.transaction_type = 'INTERNAL_TRANSFER'
              AND lt.status = 'POSTED'
              AND lt.business_date = :businessDate
            ORDER BY lt.posted_at, lt.ledger_transaction_id
            """.trimIndent(),
            mapOf("businessDate" to businessDate)
        ) { rs, _ ->
            ReconciliationExternalEntryDto(
                referenceId = rs.getString("ledger_transaction_id"),
                businessDate = rs.getObject("business_date", LocalDate::class.java),
                amountMinor = rs.getLong("amount_minor"),
                status = "SETTLED"
            )
        }

    private fun simulateExternalFile(
        businessDate: LocalDate,
        mode: String,
        internalEntries: List<ReconciliationExternalEntryDto>
    ): ReconciliationExternalFileDto {
        val normalizedMode = mode.uppercase()
        val entries = when {
            normalizedMode == "MATCHED" -> internalEntries
            normalizedMode == "EXTERNAL_ONLY" || internalEntries.isEmpty() -> listOf(
                ReconciliationExternalEntryDto(
                    referenceId = "EXT-ONLY-$businessDate",
                    businessDate = businessDate,
                    amountMinor = 1_000,
                    status = "SETTLED"
                )
            )
            normalizedMode == "MISSING_EXTERNAL" -> internalEntries.drop(1)
            normalizedMode == "DUPLICATE" -> listOf(internalEntries.first(), internalEntries.first()) + internalEntries.drop(1)
            normalizedMode == "STALE" -> listOf(
                internalEntries.first().copy(
                    businessDate = businessDate.minusDays(1),
                    status = "STALE"
                )
            ) + internalEntries.drop(1)
            normalizedMode == "STATUS_MISMATCH" -> listOf(
                internalEntries.first().copy(status = "PENDING")
            ) + internalEntries.drop(1)
            else -> internalEntries.mapIndexed { index, entry ->
                if (index == 0) {
                    entry.copy(amountMinor = entry.amountMinor + 1_000, status = "SETTLED")
                } else {
                    entry
                }
            }
        }
        return ReconciliationExternalFileDto(
            fileId = "EXT-FILE-${UUID.randomUUID().toString().uppercase()}",
            businessDate = businessDate,
            entries = entries
        )
    }

    private fun unbalancedTransactionCount(): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM (
              SELECT ledger_transaction_id,
                     currency,
                     SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS signed_total
              FROM ledger_postings
              GROUP BY ledger_transaction_id, currency
            ) totals
            WHERE signed_total <> 0
            """.trimIndent(),
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun appendAudit(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String,
        businessReferenceId: String,
        accountId: String?,
        reason: String?,
        payload: Map<String, Any?>
    ): String {
        return auditEvents.append(
            eventType = eventType,
            actorType = "STAFF",
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )
    }

    private companion object {
        const val SERIALIZABLE_RECONCILIATION_ADJUSTMENT_MAX_ATTEMPTS = 5
    }
}
