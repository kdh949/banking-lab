package lab.banking.core.complaint

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.approval.ApprovalBusinessTypes
import lab.banking.core.approval.ApprovalStatus
import lab.banking.core.approval.OperatorApproval
import lab.banking.core.approval.PersistentApprovalService
import lab.banking.core.approval.SubmitApprovalCommand
import lab.banking.core.audit.AuditEventAppender
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
class ComplaintCaseService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val approvals: PersistentApprovalService,
    private val auditEvents: AuditEventAppender,
    private val transactionManager: PlatformTransactionManager
) {
    @Transactional(readOnly = true)
    fun list(): List<ComplaintCaseDto> =
        jdbc.query(
            complaintSql("ORDER BY created_at, complaint_case_id"),
            emptyMap<String, Any?>(),
            this::mapCase
        )

    @Transactional(readOnly = true)
    fun find(caseId: String): ComplaintCaseDto =
        findForRead(caseId)

    @Transactional
    fun listCustomerComplaints(customerId: String): CustomerComplaintListResponse {
        val resolvedCustomerId = customerId.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("customerId is required for complaint list")
        BankingLabAuthContext.requireCustomerOwnership(resolvedCustomerId)
        val items = jdbc.query(
            complaintSql(
                """
                WHERE customer_id = :customerId
                ORDER BY created_at, complaint_case_id
                """.trimIndent()
            ),
            mapOf("customerId" to resolvedCustomerId),
            this::mapCase
        )
        appendCustomerComplaintViewAudit(resolvedCustomerId, items)
        return CustomerComplaintListResponse(items = items)
    }

    fun createCustomerComplaint(command: CustomerComplaintEntryCommand): CustomerComplaintEntryResponse {
        return runSerializableComplaintCommand {
            createCustomerComplaintInTransaction(command)
        }
    }

    fun confirmCustomerComplaint(
        caseId: String,
        command: CustomerComplaintConfirmCommand
    ): CustomerComplaintConfirmResponse {
        return runSerializableComplaintCommand {
            confirmCustomerComplaintInTransaction(caseId, command)
        }
    }

    fun submitCustomerComplaintMaterial(
        caseId: String,
        command: CustomerComplaintMaterialCommand
    ): CustomerComplaintMaterialResponse {
        return runSerializableComplaintCommand {
            submitCustomerComplaintMaterialInTransaction(caseId, command)
        }
    }

    fun reopenCustomerComplaint(
        caseId: String,
        command: CustomerComplaintReopenCommand
    ): CustomerComplaintReopenResponse {
        return runSerializableComplaintCommand {
            reopenCustomerComplaintInTransaction(caseId, command)
        }
    }

    fun complaintTypeGuide(): ComplaintTypeGuideResponse =
        ComplaintTypeGuideResponse(
            items = listOf(
                ComplaintTypeGuideDto(
                    category = "TRANSFER_DISPUTE",
                    description = "Synthetic transfer dispute or wrong-transfer investigation.",
                    slaHours = 72,
                    requiredMaterials = listOf("transactionId", "customerStatement"),
                    sourceReferenceTypes = listOf("CUSTOMER_TRANSFER", "LEDGER_TRANSACTION")
                ),
                ComplaintTypeGuideDto(
                    category = "ACCOUNT_ACCESS",
                    description = "Synthetic account access, statement, or login-support complaint.",
                    slaHours = 72,
                    requiredMaterials = listOf("screenId", "observedAt")
                ),
                ComplaintTypeGuideDto(
                    category = "FEE_INQUIRY",
                    description = "Synthetic fee explanation, waiver follow-up, or refund review.",
                    slaHours = 72,
                    requiredMaterials = listOf("accountId", "feeTransactionId")
                ),
                ComplaintTypeGuideDto(
                    category = "CARD_DISPUTE",
                    description = "Synthetic card authorization, capture, or lost-card dispute.",
                    slaHours = 72,
                    requiredMaterials = listOf("cardId", "authorizationId"),
                    sourceReferenceTypes = listOf("CARD_AUTHORIZATION", "CARD_CAPTURE")
                )
            )
        )

    private fun createCustomerComplaintInTransaction(command: CustomerComplaintEntryCommand): CustomerComplaintEntryResponse {
        val customerId = command.customerId?.takeIf { it.isNotBlank() }
            ?: BankingLabAuthContext.get()?.customerId
            ?: throw WorkflowErrors.validation("customerId is required for complaint entry")
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        val category = command.category?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.uppercase()
            ?: throw WorkflowErrors.validation("complaint category is required")
        val description = command.description?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("complaint description is required")
        val sourceReference = validateSourceReference(customerId, category, command.sourceReference)
        val actorId = BankingLabAuthContext.get()?.subject
            ?: command.requestedBy?.takeIf { it.isNotBlank() }
            ?: customerId
        val caseId = "CMP-${UUID.randomUUID().toString().uppercase()}"

        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id, source_reference_json
            )
            VALUES (
              :caseId, :customerId, :category, :description, 'RECEIVED',
              now() + interval '7 days', NULL, NULL, CAST(:sourceReferenceJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "customerId" to customerId,
                "category" to category,
                "description" to description,
                "sourceReferenceJson" to sourceReference?.let { objectMapper.writeValueAsString(it) }
            )
        )
        appendTimeline(caseId, "RECEIVED", null, "RECEIVED", actorId, "customer complaint received")
        auditEvents.append(
            eventType = "COMMAND_REQUESTED",
            actorType = "CUSTOMER",
            actorId = actorId,
            actorRole = "CUSTOMER",
            screenId = "CWB-301",
            businessReferenceId = caseId,
            customerId = customerId,
            reason = command.reason ?: "Customer complaint entry",
            payload = mapOf(
                "businessType" to "COMPLAINT_ENTRY",
                "category" to category,
                "sourceReference" to sourceReference?.let {
                    mapOf(
                        "sourceType" to it.sourceType,
                        "sourceId" to it.sourceId,
                        "accountId" to it.accountId,
                        "cardId" to it.cardId,
                        "ledgerTransactionId" to it.ledgerTransactionId,
                        "amountMinor" to it.amountMinor,
                        "currency" to it.currency,
                        "businessDate" to it.businessDate?.toString(),
                        "syntheticOnly" to true
                    )
                },
                "syntheticOnly" to true
            )
        )
        return CustomerComplaintEntryResponse(item = findForRead(caseId))
    }

    private fun validateSourceReference(
        customerId: String,
        category: String,
        sourceReference: ComplaintSourceReferenceDto?
    ): ComplaintSourceReferenceDto? {
        val allowedTypes = when (category) {
            "TRANSFER_DISPUTE" -> TRANSFER_DISPUTE_SOURCE_TYPES
            "CARD_DISPUTE" -> CARD_DISPUTE_SOURCE_TYPES
            else -> null
        }
        if (allowedTypes == null) {
            if (sourceReference != null) {
                throw WorkflowErrors.validation("sourceReference is supported only for transfer or card disputes")
            }
            return null
        }

        val reference = sourceReference ?: throw WorkflowErrors.validation("sourceReference is required for $category")
        val sourceType = reference.sourceType.trim().uppercase()
        val sourceId = reference.sourceId.trim()
        if (sourceType !in allowedTypes) {
            throw WorkflowErrors.validation("$category sourceType must be one of ${allowedTypes.joinToString(", ")}")
        }
        if (sourceId.isBlank()) {
            throw WorkflowErrors.validation("sourceReference.sourceId is required")
        }
        if (!reference.syntheticOnly) {
            throw WorkflowErrors.validation("sourceReference must be syntheticOnly")
        }
        if (reference.amountMinor != null && reference.amountMinor <= 0) {
            throw WorkflowErrors.validation("sourceReference.amountMinor must be positive when supplied")
        }

        val normalized = reference.copy(
            sourceType = sourceType,
            sourceId = sourceId,
            accountId = reference.accountId.trimToNull(),
            cardId = reference.cardId.trimToNull(),
            ledgerTransactionId = reference.ledgerTransactionId.trimToNull(),
            currency = reference.currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "KRW",
            syntheticOnly = true
        )
        when (sourceType) {
            "CUSTOMER_TRANSFER", "LEDGER_TRANSACTION" -> requireTransferSourceOwned(customerId, normalized)
            "CARD_AUTHORIZATION", "CARD_CAPTURE" -> requireCardSourceOwned(customerId, normalized)
        }
        return normalized
    }

    private fun requireTransferSourceOwned(customerId: String, sourceReference: ComplaintSourceReferenceDto) {
        val owned = when (sourceReference.sourceType) {
            "CUSTOMER_TRANSFER" ->
                countRows(
                    """
                    SELECT count(*)
                    FROM customer_transfer_results
                    WHERE customer_id = :customerId
                      AND result_id = :sourceId
                    """.trimIndent(),
                    mapOf("customerId" to customerId, "sourceId" to sourceReference.sourceId)
                )
            "LEDGER_TRANSACTION" ->
                countRows(
                    """
                    SELECT count(DISTINCT lt.ledger_transaction_id)
                    FROM ledger_transactions lt
                    JOIN ledger_postings lp
                      ON lp.ledger_transaction_id = lt.ledger_transaction_id
                    JOIN accounts a
                      ON a.account_id = lp.account_id
                    WHERE lt.ledger_transaction_id = :sourceId
                      AND a.customer_id = :customerId
                      AND (CAST(:accountId AS text) IS NULL OR a.account_id = CAST(:accountId AS text))
                    """.trimIndent(),
                    mapOf(
                        "customerId" to customerId,
                        "sourceId" to sourceReference.sourceId,
                        "accountId" to sourceReference.accountId
                    )
                )
            else -> 0
        }
        if (owned == 0) {
            throw WorkflowErrors.authorizationViolation("complaint source reference does not belong to customer")
        }
    }

    private fun requireCardSourceOwned(customerId: String, sourceReference: ComplaintSourceReferenceDto) {
        val owned = when (sourceReference.sourceType) {
            "CARD_AUTHORIZATION" ->
                countRows(
                    """
                    SELECT count(*)
                    FROM card_authorizations ca
                    JOIN cards c
                      ON c.card_id = ca.card_id
                    WHERE ca.authorization_id = :sourceId
                      AND c.customer_id = :customerId
                      AND (CAST(:cardId AS text) IS NULL OR c.card_id = CAST(:cardId AS text))
                      AND (CAST(:accountId AS text) IS NULL OR ca.account_id = CAST(:accountId AS text))
                    """.trimIndent(),
                    mapOf(
                        "customerId" to customerId,
                        "sourceId" to sourceReference.sourceId,
                        "cardId" to sourceReference.cardId,
                        "accountId" to sourceReference.accountId
                    )
                )
            "CARD_CAPTURE" ->
                countRows(
                    """
                    SELECT count(*)
                    FROM card_captures cc
                    JOIN cards c
                      ON c.card_id = cc.card_id
                    WHERE cc.capture_id = :sourceId
                      AND c.customer_id = :customerId
                      AND (CAST(:cardId AS text) IS NULL OR c.card_id = CAST(:cardId AS text))
                      AND (
                        CAST(:ledgerTransactionId AS text) IS NULL
                        OR cc.ledger_transaction_id = CAST(:ledgerTransactionId AS text)
                      )
                    """.trimIndent(),
                    mapOf(
                        "customerId" to customerId,
                        "sourceId" to sourceReference.sourceId,
                        "cardId" to sourceReference.cardId,
                        "ledgerTransactionId" to sourceReference.ledgerTransactionId
                    )
                )
            else -> 0
        }
        if (owned == 0) {
            throw WorkflowErrors.authorizationViolation("complaint source reference does not belong to customer")
        }
    }

    private fun countRows(sql: String, params: Map<String, Any?>): Int =
        jdbc.queryForObject(sql, params, Int::class.java) ?: 0

    private fun confirmCustomerComplaintInTransaction(
        caseId: String,
        command: CustomerComplaintConfirmCommand
    ): CustomerComplaintConfirmResponse {
        val complaint = findForUpdate(caseId)
        val customerId = command.customerId?.takeIf { it.isNotBlank() }
            ?: BankingLabAuthContext.get()?.customerId
            ?: complaint.customerId
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        if (complaint.customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("complaint does not belong to customer")
        }
        if (complaint.status != "ANSWERED") {
            throw WorkflowErrors.stateViolation("complaint is not answered: ${complaint.status}")
        }
        val now = OffsetDateTime.now()
        jdbc.update(
            """
            UPDATE complaint_cases
            SET status = 'CLOSED',
                customer_confirmed_at = :customerConfirmedAt,
                updated_at = now()
            WHERE complaint_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to complaint.caseId,
                "customerConfirmedAt" to now
            )
        )
        val actorId = BankingLabAuthContext.get()?.subject ?: customerId
        appendTimeline(complaint.caseId, "CLOSED", complaint.status, "CLOSED", actorId, command.note)
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "CUSTOMER",
            actorId = actorId,
            actorRole = "CUSTOMER",
            screenId = "CMP-101",
            businessReferenceId = complaint.caseId,
            customerId = customerId,
            reason = command.reason ?: "Customer confirmed complaint answer",
            payload = mapOf(
                "status" to "CLOSED",
                "syntheticOnly" to true
            )
        )
        return CustomerComplaintConfirmResponse(item = findForRead(complaint.caseId))
    }

    private fun submitCustomerComplaintMaterialInTransaction(
        caseId: String,
        command: CustomerComplaintMaterialCommand
    ): CustomerComplaintMaterialResponse {
        val complaint = findForUpdate(caseId)
        val customerId = command.customerId?.takeIf { it.isNotBlank() }
            ?: BankingLabAuthContext.get()?.customerId
            ?: complaint.customerId
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        if (complaint.customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("complaint does not belong to customer")
        }
        if (complaint.status == "CLOSED") {
            throw WorkflowErrors.stateViolation("closed complaint must be reopened before submitting materials")
        }
        if (complaint.status == "WAITING_APPROVAL") {
            throw WorkflowErrors.stateViolation("complaint is waiting for answer approval")
        }
        val materialType = command.materialType?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("materialType is required")
        val fileName = command.fileName?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("fileName is required")
        val materialId = "CMM-${UUID.randomUUID().toString().uppercase()}"
        val syntheticStorageRef = command.syntheticStorageRef?.takeIf { it.isNotBlank() }
            ?: "synthetic://complaints/$caseId/materials/$materialId"
        if (!syntheticStorageRef.startsWith("synthetic://")) {
            throw WorkflowErrors.validation("syntheticStorageRef must use synthetic://")
        }
        val actorId = BankingLabAuthContext.get()?.subject ?: customerId
        jdbc.update(
            """
            INSERT INTO complaint_case_materials (
              complaint_material_id, complaint_case_id, customer_id, material_type,
              file_name, description, synthetic_storage_ref, submitted_by
            )
            VALUES (
              :materialId, :caseId, :customerId, :materialType,
              :fileName, :description, :syntheticStorageRef, :submittedBy
            )
            """.trimIndent(),
            mapOf(
                "materialId" to materialId,
                "caseId" to complaint.caseId,
                "customerId" to customerId,
                "materialType" to materialType,
                "fileName" to fileName,
                "description" to command.description,
                "syntheticStorageRef" to syntheticStorageRef,
                "submittedBy" to actorId
            )
        )
        val nextStatus = if (complaint.status == "WAITING_CUSTOMER") "IN_REVIEW" else complaint.status
        if (nextStatus != complaint.status) {
            jdbc.update(
                """
                UPDATE complaint_cases
                SET status = :status,
                    updated_at = now()
                WHERE complaint_case_id = :caseId
                """.trimIndent(),
                mapOf(
                    "caseId" to complaint.caseId,
                    "status" to nextStatus
                )
            )
        } else {
            jdbc.update(
                """
                UPDATE complaint_cases
                SET updated_at = now()
                WHERE complaint_case_id = :caseId
                """.trimIndent(),
                mapOf("caseId" to complaint.caseId)
            )
        }
        appendTimeline(
            complaint.caseId,
            "MATERIAL_SUBMITTED",
            complaint.status,
            nextStatus,
            actorId,
            command.reason ?: "customer submitted additional material"
        )
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "CUSTOMER",
            actorId = actorId,
            actorRole = "CUSTOMER",
            screenId = "CMP-103",
            businessReferenceId = complaint.caseId,
            customerId = customerId,
            reason = command.reason ?: "Customer submitted complaint material",
            payload = mapOf(
                "materialId" to materialId,
                "materialType" to materialType,
                "fileName" to fileName,
                "syntheticStorageRef" to syntheticStorageRef,
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
        return CustomerComplaintMaterialResponse(
            item = findForRead(complaint.caseId),
            material = findMaterial(materialId)
        )
    }

    private fun reopenCustomerComplaintInTransaction(
        caseId: String,
        command: CustomerComplaintReopenCommand
    ): CustomerComplaintReopenResponse {
        val complaint = findForUpdate(caseId)
        val customerId = command.customerId?.takeIf { it.isNotBlank() }
            ?: BankingLabAuthContext.get()?.customerId
            ?: complaint.customerId
        BankingLabAuthContext.requireCustomerOwnership(customerId)
        if (complaint.customerId != customerId) {
            throw WorkflowErrors.authorizationViolation("complaint does not belong to customer")
        }
        if (complaint.status != "CLOSED") {
            throw WorkflowErrors.stateViolation("only closed complaints can be reopened: ${complaint.status}")
        }
        val reopenReason = command.reopenReason?.takeIf { it.isNotBlank() }
            ?: throw WorkflowErrors.validation("reopenReason is required")
        val reopenRequestId = "CMR-${UUID.randomUUID().toString().uppercase()}"
        val actorId = BankingLabAuthContext.get()?.subject ?: customerId
        jdbc.update(
            """
            INSERT INTO complaint_reopen_requests (
              complaint_reopen_request_id, complaint_case_id, customer_id, reopen_reason,
              status, requested_by
            )
            VALUES (
              :reopenRequestId, :caseId, :customerId, :reopenReason,
              'REOPENED', :requestedBy
            )
            """.trimIndent(),
            mapOf(
                "reopenRequestId" to reopenRequestId,
                "caseId" to complaint.caseId,
                "customerId" to customerId,
                "reopenReason" to reopenReason,
                "requestedBy" to actorId
            )
        )
        jdbc.update(
            """
            UPDATE complaint_cases
            SET status = 'REOPENED',
                updated_at = now()
            WHERE complaint_case_id = :caseId
            """.trimIndent(),
            mapOf("caseId" to complaint.caseId)
        )
        appendTimeline(complaint.caseId, "REOPEN_REQUESTED", complaint.status, "REOPENED", actorId, reopenReason)
        auditEvents.append(
            eventType = "COMMAND_EXECUTED",
            actorType = "CUSTOMER",
            actorId = actorId,
            actorRole = "CUSTOMER",
            screenId = "CMP-106",
            businessReferenceId = complaint.caseId,
            customerId = customerId,
            reason = command.reason ?: "Customer requested complaint reopen",
            payload = mapOf(
                "reopenRequestId" to reopenRequestId,
                "status" to "REOPENED",
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
        return CustomerComplaintReopenResponse(
            item = findForRead(complaint.caseId),
            reopenRequest = findReopenRequest(reopenRequestId)
        )
    }

    private fun <T> runSerializableComplaintCommand(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val template = TransactionTemplate(transactionManager).apply {
                    isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
                }
                return template.execute { operation() }
                    ?: error("serializable complaint command returned no result")
            } catch (error: RuntimeException) {
                if (attempt >= SERIALIZABLE_COMPLAINT_COMMAND_MAX_ATTEMPTS || !isRetryableSerializationFailure(error)) {
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
    fun draftAnswer(caseId: String, command: ComplaintAnswerDraftCommand): ComplaintAnswerDraftResponse {
        if (command.body.isNullOrBlank()) {
            throw WorkflowErrors.validation("answer draft body is required")
        }
        if (command.reason.isNullOrBlank()) {
            throw WorkflowErrors.reasonRequired("COMPLAINT_ANSWER_SEND requires a business reason")
        }
        val complaint = findForUpdate(caseId)
        if (complaint.status != "IN_REVIEW") {
            throw WorkflowErrors.stateViolation("complaint is not in review: ${complaint.status}")
        }
        val actorId = command.actorId ?: "complaint01"
        val draft = ComplaintAnswerDraftDto(
            body = command.body,
            draftedBy = actorId,
            draftedAt = OffsetDateTime.now()
        )
        val approval = approvals.submit(
            SubmitApprovalCommand(
                businessType = ApprovalBusinessTypes.COMPLAINT_ANSWER_SEND,
                businessReferenceId = caseId,
                requestedBy = actorId,
                requestedByRole = command.requestedByRole ?: "COMPLAINT_HANDLER",
                requestReason = command.reason,
                beforeSnapshot = mapOf("status" to complaint.status, "answer" to complaint.answer),
                afterSnapshot = mapOf("answerBody" to command.body),
                screenId = "CMP-201"
            )
        )
        updateComplaintForDraft(caseId, draft, approval.approvalId)
        appendTimeline(caseId, "WAITING_APPROVAL", complaint.status, "WAITING_APPROVAL", actorId, "answer draft prepared")
        return ComplaintAnswerDraftResponse(item = findForRead(caseId), approval = approval)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun applyApprovedAnswer(approval: OperatorApproval): ComplaintCaseDto {
        if (approval.status != ApprovalStatus.APPROVED) {
            throw WorkflowErrors.stateViolation("approval is not approved: ${approval.status}")
        }
        if (approval.businessType != ApprovalBusinessTypes.COMPLAINT_ANSWER_SEND) {
            throw WorkflowErrors.stateViolation("approval is not a complaint answer approval")
        }
        val complaint = findForUpdate(approval.businessReferenceId)
        if (complaint.status != "WAITING_APPROVAL") {
            throw WorkflowErrors.stateViolation("complaint is not waiting for answer approval: ${complaint.status}")
        }
        val draft = complaint.answerDraft ?: throw WorkflowErrors.stateViolation("complaint answer draft is missing")
        val answeredBy = approval.approvedBy ?: "CHECKER"
        val answer = ComplaintAnswerDto(
            body = draft.body,
            answeredBy = answeredBy,
            answeredAt = OffsetDateTime.now()
        )
        jdbc.update(
            """
            UPDATE complaint_cases
            SET status = 'ANSWERED',
                answer_json = CAST(:answerJson AS jsonb),
                answer_draft_json = NULL,
                approval_id = NULL,
                updated_at = now()
            WHERE complaint_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to complaint.caseId,
                "answerJson" to objectMapper.writeValueAsString(answer)
            )
        )
        appendTimeline(complaint.caseId, "ANSWERED", complaint.status, "ANSWERED", answeredBy, "answer sent")
        return findForRead(complaint.caseId)
    }

    private fun appendCustomerComplaintViewAudit(customerId: String, items: List<ComplaintCaseDto>) {
        val principal = BankingLabAuthContext.get()
        auditEvents.append(
            eventType = "COMPLAINT_VIEW",
            actorType = "CUSTOMER",
            actorId = principal?.subject ?: customerId,
            actorRole = principal?.roles?.sorted()?.joinToString(",") ?: "CUSTOMER",
            screenId = "CMP-102",
            businessReferenceId = customerId,
            customerId = customerId,
            reason = null,
            payload = mapOf(
                "caseCount" to items.size,
                "cases" to items.map {
                    mapOf(
                        "caseId" to it.caseId,
                        "category" to it.category,
                        "status" to it.status,
                        "slaDueAt" to it.slaDueAt.toString()
                    )
                },
                "maskingPolicy" to "CUSTOMER_SELF",
                "syntheticOnly" to true
            )
        )
    }

    private fun updateComplaintForDraft(caseId: String, draft: ComplaintAnswerDraftDto, approvalId: String) {
        jdbc.update(
            """
            UPDATE complaint_cases
            SET status = 'WAITING_APPROVAL',
                answer_draft_json = CAST(:draftJson AS jsonb),
                approval_id = :approvalId,
                updated_at = now()
            WHERE complaint_case_id = :caseId
            """.trimIndent(),
            mapOf(
                "caseId" to caseId,
                "draftJson" to objectMapper.writeValueAsString(draft),
                "approvalId" to approvalId
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
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              :timelineId, :caseId, :eventType, :fromStatus,
              :toStatus, :actorId, :note, '{}'::jsonb
            )
            """.trimIndent(),
            mapOf(
                "timelineId" to "CMT-${UUID.randomUUID().toString().uppercase()}",
                "caseId" to caseId,
                "eventType" to eventType,
                "fromStatus" to fromStatus,
                "toStatus" to toStatus,
                "actorId" to actorId,
                "note" to note
            )
        )
    }

    private fun findForRead(caseId: String): ComplaintCaseDto =
        jdbc.queryForObject(
            complaintSql("WHERE complaint_case_id = :caseId"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("complaint not found: $caseId")

    private fun findForUpdate(caseId: String): ComplaintCaseDto =
        jdbc.queryForObject(
            complaintSql("WHERE complaint_case_id = :caseId FOR UPDATE"),
            mapOf("caseId" to caseId),
            this::mapCase
        ) ?: throw WorkflowErrors.notFound("complaint not found: $caseId")

    private fun findMaterial(materialId: String): ComplaintMaterialDto =
        jdbc.queryForObject(
            """
            SELECT complaint_material_id, complaint_case_id, customer_id, material_type,
                   file_name, description, synthetic_storage_ref, submitted_by, created_at
            FROM complaint_case_materials
            WHERE complaint_material_id = :materialId
            """.trimIndent(),
            mapOf("materialId" to materialId)
        ) { rs, _ ->
            ComplaintMaterialDto(
                materialId = rs.getString("complaint_material_id"),
                caseId = rs.getString("complaint_case_id"),
                customerId = rs.getString("customer_id"),
                materialType = rs.getString("material_type"),
                fileName = rs.getString("file_name"),
                description = rs.getString("description"),
                syntheticStorageRef = rs.getString("synthetic_storage_ref"),
                submittedBy = rs.getString("submitted_by"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        } ?: throw WorkflowErrors.notFound("complaint material not found: $materialId")

    private fun findReopenRequest(reopenRequestId: String): ComplaintReopenRequestDto =
        jdbc.queryForObject(
            """
            SELECT complaint_reopen_request_id, complaint_case_id, customer_id, reopen_reason,
                   status, requested_by, created_at
            FROM complaint_reopen_requests
            WHERE complaint_reopen_request_id = :reopenRequestId
            """.trimIndent(),
            mapOf("reopenRequestId" to reopenRequestId)
        ) { rs, _ ->
            ComplaintReopenRequestDto(
                reopenRequestId = rs.getString("complaint_reopen_request_id"),
                caseId = rs.getString("complaint_case_id"),
                customerId = rs.getString("customer_id"),
                reopenReason = rs.getString("reopen_reason"),
                status = rs.getString("status"),
                requestedBy = rs.getString("requested_by"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        } ?: throw WorkflowErrors.notFound("complaint reopen request not found: $reopenRequestId")

    private fun complaintSql(suffix: String): String =
        """
        SELECT complaint_case_id, customer_id, category, description, status,
               sla_due_at, classification, owner_id, answer_json::text AS answer_json,
               answer_draft_json::text AS answer_draft_json, approval_id,
               customer_confirmed_at, source_reference_json::text AS source_reference_json,
               temporal_workflow_id, temporal_run_id, created_at
        FROM complaint_cases
        $suffix
        """.trimIndent()

    private fun mapCase(rs: ResultSet, rowNum: Int): ComplaintCaseDto =
        ComplaintCaseDto(
            caseId = rs.getString("complaint_case_id"),
            customerId = rs.getString("customer_id"),
            category = rs.getString("category"),
            description = rs.getString("description"),
            status = rs.getString("status"),
            slaDueAt = rs.getObject("sla_due_at", OffsetDateTime::class.java),
            classification = rs.getString("classification"),
            owner = rs.getString("owner_id"),
            answer = readValue(rs.getString("answer_json"), ComplaintAnswerDto::class.java),
            answerDraft = readValue(rs.getString("answer_draft_json"), ComplaintAnswerDraftDto::class.java),
            approvalId = rs.getString("approval_id"),
            customerConfirmedAt = rs.getObject("customer_confirmed_at", OffsetDateTime::class.java),
            timeline = timelineFor(rs.getString("complaint_case_id")),
            sourceReference = readValue(rs.getString("source_reference_json"), ComplaintSourceReferenceDto::class.java),
            temporalWorkflow = temporalReference(rs)
        )

    private fun timelineFor(caseId: String): List<ComplaintTimelineEntryDto> =
        jdbc.query(
            """
            SELECT event_type, from_status, to_status, note, created_at
            FROM complaint_case_timeline
            WHERE complaint_case_id = :caseId
            ORDER BY created_at, complaint_timeline_id
            """.trimIndent(),
            mapOf("caseId" to caseId)
        ) { rs, _ ->
            ComplaintTimelineEntryDto(
                type = rs.getString("event_type"),
                from = rs.getString("from_status"),
                to = rs.getString("to_status"),
                note = rs.getString("note"),
                at = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }

    private fun <T> readValue(payloadJson: String?, valueType: Class<T>): T? =
        payloadJson?.let { objectMapper.readValue(it, valueType) }

    private fun temporalReference(rs: ResultSet): TemporalWorkflowReference? {
        val workflowId = rs.getString("temporal_workflow_id") ?: return null
        return TemporalWorkflowReference(
            workflowId = workflowId,
            runId = rs.getString("temporal_run_id")
        )
    }

    private fun String?.trimToNull(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

    private companion object {
        const val SERIALIZABLE_COMPLAINT_COMMAND_MAX_ATTEMPTS = 5
        val TRANSFER_DISPUTE_SOURCE_TYPES = setOf("CUSTOMER_TRANSFER", "LEDGER_TRANSACTION")
        val CARD_DISPUTE_SOURCE_TYPES = setOf("CARD_AUTHORIZATION", "CARD_CAPTURE")
    }
}
