package lab.banking.core.callcenter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.security.BankingLabAuthContext
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class CallCenterService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun searchCustomers(query: String, reason: String?): CallCenterCustomerSearchResponse {
        val viewReason = requireReason(reason, "call-center customer search requires a business reason")
        val actor = resolveActor(null, null, READ_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val customers = jdbc.query(
            """
            SELECT customer_id, customer_name, customer_phone, customer_grade, risk_grade
            FROM customers
            WHERE customer_id <> 'BANK'
              AND (:query = ''
                   OR customer_id ILIKE :queryPattern
                   OR customer_name ILIKE :queryPattern)
            ORDER BY customer_id
            LIMIT 25
            """.trimIndent(),
            mapOf("query" to query.trim(), "queryPattern" to "%${query.trim()}%"),
            this::mapCustomerSummary
        )
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_CUSTOMER_SEARCH",
            actor = actor,
            screenId = "CALL-101",
            interactionId = null,
            customerId = customers.firstOrNull()?.customerId,
            accountId = null,
            reason = viewReason,
            payload = mapOf(
                "query" to query.trim(),
                "resultCount" to customers.size,
                "piiExposure" to "MASKED",
                "syntheticOnly" to true
            )
        )
        if (customers.size == 1) {
            appendAccessAudit(
                auditEventId = auditEventId,
                interactionId = null,
                customerId = customers.single().customerId,
                actor = actor,
                screenId = "CALL-101",
                actionType = "CUSTOMER_SEARCH",
                reason = viewReason
            )
        }
        return CallCenterCustomerSearchResponse(auditEventId = auditEventId, items = customers)
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun startInteraction(command: StartCallCenterInteractionCommand): CallCenterInteractionResponse {
        val reason = requireReason(command.reason, "call-center interaction start requires a business reason")
        val actor = resolveActor(command.requestedBy, command.requestedByRole, COMMAND_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val customerId = requireField(command.customerId, "customerId")
        val channel = normalize(command.channel, "channel", CHANNELS)
        val contactReasonCode = requireField(command.contactReasonCode, "contactReasonCode").uppercase()
        requireCustomer(customerId)
        command.accountId?.takeIf { it.isNotBlank() }?.let { requireCustomerAccount(customerId, it) }
        val interactionId = "CALL-${UUID.randomUUID().toString().uppercase()}"
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_INTERACTION_STARTED",
            actor = actor,
            screenId = "CALL-102",
            interactionId = interactionId,
            customerId = customerId,
            accountId = command.accountId?.trimToNull(),
            reason = reason,
            payload = mapOf(
                "channel" to channel,
                "contactReasonCode" to contactReasonCode,
                "piiExposure" to "MASKED",
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            INSERT INTO call_center_interactions (
              interaction_id, customer_id, account_id, channel, contact_reason_code,
              status, created_by, created_by_role, assigned_to, reason,
              metadata_json, audit_event_id
            )
            VALUES (
              :interactionId, :customerId, :accountId, :channel, :contactReasonCode,
              'OPEN', :createdBy, :createdByRole, :assignedTo, :reason,
              CAST(:metadataJson AS jsonb), :auditEventId
            )
            """.trimIndent(),
            mapOf(
                "interactionId" to interactionId,
                "customerId" to customerId,
                "accountId" to command.accountId.trimToNull(),
                "channel" to channel,
                "contactReasonCode" to contactReasonCode,
                "createdBy" to actor.id,
                "createdByRole" to actor.role,
                "assignedTo" to command.assignedTo.trimToNull(),
                "reason" to reason,
                "metadataJson" to objectMapper.writeValueAsString(syntheticMetadata(command.metadata)),
                "auditEventId" to auditEventId
            )
        )
        appendAccessAudit(auditEventId, interactionId, customerId, actor, "CALL-102", "INTERACTION_STARTED", reason)
        return CallCenterInteractionResponse(findInteraction(interactionId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun interaction(interactionId: String, reason: String?): CallCenterInteractionResponse {
        val viewReason = requireReason(reason, "call-center interaction detail requires a business reason")
        val interaction = findInteractionRecord(interactionId)
        val actor = resolveActor(null, null, READ_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_INTERACTION_VIEW",
            actor = actor,
            screenId = "CALL-102",
            interactionId = interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            reason = viewReason,
            payload = mapOf("piiExposure" to "MASKED", "syntheticOnly" to true)
        )
        appendAccessAudit(auditEventId, interactionId, interaction.customerId, actor, "CALL-102", "INTERACTION_VIEW", viewReason)
        return CallCenterInteractionResponse(findInteraction(interactionId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun addNote(interactionId: String, command: CallCenterNoteCommand): CallCenterNoteResponse {
        val reason = requireReason(command.reason, "call-center note requires a business reason")
        val actor = resolveActor(command.requestedBy, command.requestedByRole, COMMAND_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val interaction = findInteractionRecordForUpdate(interactionId)
        requireOpenInteraction(interaction)
        val body = requireField(command.noteBody, "noteBody")
        val redaction = redactNote(body)
        val noteId = "CALL-NOTE-${UUID.randomUUID().toString().uppercase()}"
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_NOTE_ADDED",
            actor = actor,
            screenId = "CALL-103",
            interactionId = interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            reason = reason,
            payload = mapOf(
                "noteId" to noteId,
                "noteLength" to redaction.redacted.length,
                "redactionApplied" to redaction.applied,
                "piiPatternCount" to redaction.patternCount,
                "rawNoteCopiedToAudit" to false,
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            INSERT INTO call_center_notes (
              note_id, interaction_id, customer_id, created_by, created_by_role,
              note_body_redacted, redaction_applied, pii_pattern_count,
              reason, audit_event_id
            )
            VALUES (
              :noteId, :interactionId, :customerId, :createdBy, :createdByRole,
              :noteBodyRedacted, :redactionApplied, :piiPatternCount,
              :reason, :auditEventId
            )
            """.trimIndent(),
            mapOf(
                "noteId" to noteId,
                "interactionId" to interactionId,
                "customerId" to interaction.customerId,
                "createdBy" to actor.id,
                "createdByRole" to actor.role,
                "noteBodyRedacted" to redaction.redacted,
                "redactionApplied" to redaction.applied,
                "piiPatternCount" to redaction.patternCount,
                "reason" to reason,
                "auditEventId" to auditEventId
            )
        )
        appendAccessAudit(auditEventId, interactionId, interaction.customerId, actor, "CALL-103", "NOTE_ADDED", reason)
        return CallCenterNoteResponse(item = findInteraction(interactionId), note = findNote(noteId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun createAftercallTask(interactionId: String, command: CallCenterAftercallTaskCommand): CallCenterAftercallTaskResponse {
        val reason = requireReason(command.reason, "call-center aftercall task requires a business reason")
        val actor = resolveActor(command.requestedBy, command.requestedByRole, COMMAND_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val interaction = findInteractionRecordForUpdate(interactionId)
        requireOpenInteraction(interaction)
        val taskType = requireField(command.taskType, "taskType").uppercase()
        val taskId = "CALL-TASK-${UUID.randomUUID().toString().uppercase()}"
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_AFTERCALL_TASK_CREATED",
            actor = actor,
            screenId = "CALL-104",
            interactionId = interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            reason = reason,
            payload = mapOf("taskId" to taskId, "taskType" to taskType, "syntheticOnly" to true)
        )
        jdbc.update(
            """
            INSERT INTO call_center_aftercall_tasks (
              task_id, interaction_id, customer_id, task_type, status,
              assigned_to, due_at, created_by, created_by_role, reason,
              audit_event_id, metadata_json
            )
            VALUES (
              :taskId, :interactionId, :customerId, :taskType, 'OPEN',
              :assignedTo, :dueAt, :createdBy, :createdByRole, :reason,
              :auditEventId, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "taskId" to taskId,
                "interactionId" to interactionId,
                "customerId" to interaction.customerId,
                "taskType" to taskType,
                "assignedTo" to command.assignedTo.trimToNull(),
                "dueAt" to command.dueAt,
                "createdBy" to actor.id,
                "createdByRole" to actor.role,
                "reason" to reason,
                "auditEventId" to auditEventId,
                "metadataJson" to objectMapper.writeValueAsString(syntheticMetadata(command.metadata))
            )
        )
        jdbc.update(
            """
            UPDATE call_center_interactions
            SET status = 'AFTERCALL'
            WHERE interaction_id = :interactionId
              AND status = 'OPEN'
            """.trimIndent(),
            mapOf("interactionId" to interactionId)
        )
        appendAccessAudit(auditEventId, interactionId, interaction.customerId, actor, "CALL-104", "AFTERCALL_TASK_CREATED", reason)
        return CallCenterAftercallTaskResponse(item = findInteraction(interactionId), task = findTask(taskId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun escalate(interactionId: String, command: CallCenterEscalationCommand): CallCenterEscalationResponse {
        val reason = requireReason(command.reason, "call-center escalation requires a business reason")
        val actor = resolveActor(command.requestedBy, command.requestedByRole, ESCALATION_ROLES, "CALL_CENTER_MANAGER", "callcenter-manager01")
        val interaction = findInteractionRecordForUpdate(interactionId)
        requireNotClosed(interaction)
        val escalationType = normalize(command.escalationType, "escalationType", ESCALATION_TYPES)
        val escalationId = "CALL-ESC-${UUID.randomUUID().toString().uppercase()}"
        val complaintCaseId = if (escalationType == "COMPLAINT") {
            createComplaintFromEscalation(interaction, actor, command)
        } else {
            null
        }
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_ESCALATED",
            actor = actor,
            screenId = "CALL-106",
            interactionId = interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            reason = reason,
            payload = mapOf(
                "escalationId" to escalationId,
                "escalationType" to escalationType,
                "complaintCaseId" to complaintCaseId,
                "syntheticOnly" to true
            )
        )
        jdbc.update(
            """
            INSERT INTO call_center_escalations (
              escalation_id, interaction_id, customer_id, escalation_type, status,
              complaint_case_id, requested_by, requested_by_role, reason,
              audit_event_id, metadata_json
            )
            VALUES (
              :escalationId, :interactionId, :customerId, :escalationType, 'CREATED',
              :complaintCaseId, :requestedBy, :requestedByRole, :reason,
              :auditEventId, CAST(:metadataJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "escalationId" to escalationId,
                "interactionId" to interactionId,
                "customerId" to interaction.customerId,
                "escalationType" to escalationType,
                "complaintCaseId" to complaintCaseId,
                "requestedBy" to actor.id,
                "requestedByRole" to actor.role,
                "reason" to reason,
                "auditEventId" to auditEventId,
                "metadataJson" to objectMapper.writeValueAsString(syntheticMetadata(command.metadata))
            )
        )
        jdbc.update(
            """
            UPDATE call_center_interactions
            SET status = 'ESCALATED'
            WHERE interaction_id = :interactionId
              AND status <> 'CLOSED'
            """.trimIndent(),
            mapOf("interactionId" to interactionId)
        )
        appendAccessAudit(auditEventId, interactionId, interaction.customerId, actor, "CALL-106", "ESCALATED", reason)
        return CallCenterEscalationResponse(item = findInteraction(interactionId), escalation = findEscalation(escalationId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun closeInteraction(interactionId: String, command: CloseCallCenterInteractionCommand): CallCenterInteractionResponse {
        val reason = requireReason(command.reason, "call-center close requires a business reason")
        val actor = resolveActor(command.requestedBy, command.requestedByRole, COMMAND_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val interaction = findInteractionRecordForUpdate(interactionId)
        requireNotClosed(interaction)
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_INTERACTION_CLOSED",
            actor = actor,
            screenId = "CALL-102",
            interactionId = interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            reason = reason,
            payload = mapOf("fromStatus" to interaction.status, "toStatus" to "CLOSED", "syntheticOnly" to true)
        )
        jdbc.update(
            """
            UPDATE call_center_interactions
            SET status = 'CLOSED',
                ended_at = now()
            WHERE interaction_id = :interactionId
            """.trimIndent(),
            mapOf("interactionId" to interactionId)
        )
        appendAccessAudit(auditEventId, interactionId, interaction.customerId, actor, "CALL-102", "INTERACTION_CLOSED", reason)
        return CallCenterInteractionResponse(findInteraction(interactionId))
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun customerHistory(customerId: String, reason: String?): CallCenterInteractionListResponse {
        val viewReason = requireReason(reason, "call-center customer history requires a business reason")
        requireCustomer(customerId)
        val actor = resolveActor(null, null, READ_ROLES, "CALL_CENTER_AGENT", "callcenter01")
        val items = jdbc.query(
            """
            SELECT i.interaction_id, i.customer_id, c.customer_name, i.channel,
                   i.contact_reason_code, i.status, i.assigned_to, i.started_at, i.ended_at
            FROM call_center_interactions i
            JOIN customers c ON c.customer_id = i.customer_id
            WHERE i.customer_id = :customerId
            ORDER BY i.started_at DESC, i.interaction_id
            """.trimIndent(),
            mapOf("customerId" to customerId),
            this::mapInteractionSummary
        )
        val auditEventId = appendAudit(
            eventType = "CALL_CENTER_HISTORY_VIEW",
            actor = actor,
            screenId = "CALL-105",
            interactionId = null,
            customerId = customerId,
            accountId = null,
            reason = viewReason,
            payload = mapOf("resultCount" to items.size, "piiExposure" to "MASKED", "syntheticOnly" to true)
        )
        appendAccessAudit(auditEventId, null, customerId, actor, "CALL-105", "HISTORY_VIEW", viewReason)
        return CallCenterInteractionListResponse(auditEventId = auditEventId, items = items)
    }

    private fun createComplaintFromEscalation(
        interaction: CallCenterInteractionRecord,
        actor: CallCenterActor,
        command: CallCenterEscalationCommand
    ): String {
        val complaintCaseId = "CMP-${UUID.randomUUID().toString().uppercase()}"
        val category = command.complaintCategory?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "ACCOUNT_ACCESS"
        val description = redactNote(
            command.complaintDescription?.takeIf { it.isNotBlank() }
                ?: "Synthetic complaint converted from call-center interaction ${interaction.interactionId}."
        ).redacted
        val sourceReference = mapOf(
            "sourceType" to "CALL_CENTER_INTERACTION",
            "sourceId" to interaction.interactionId,
            "accountId" to interaction.accountId,
            "syntheticOnly" to true
        )
        jdbc.update(
            """
            INSERT INTO complaint_cases (
              complaint_case_id, customer_id, category, description, status,
              sla_due_at, classification, owner_id, source_reference_json
            )
            VALUES (
              :caseId, :customerId, :category, :description, 'RECEIVED',
              now() + interval '72 hours', 'CALL_CENTER_ESCALATION', :ownerId,
              CAST(:sourceReferenceJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "caseId" to complaintCaseId,
                "customerId" to interaction.customerId,
                "category" to category,
                "description" to description,
                "ownerId" to actor.id,
                "sourceReferenceJson" to objectMapper.writeValueAsString(sourceReference)
            )
        )
        jdbc.update(
            """
            INSERT INTO complaint_case_timeline (
              complaint_timeline_id, complaint_case_id, event_type, from_status,
              to_status, actor_id, note, payload_json
            )
            VALUES (
              :timelineId, :caseId, 'CALL_CENTER_ESCALATED', NULL,
              'RECEIVED', :actorId, 'call-center escalation created complaint',
              CAST(:payloadJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "timelineId" to "CMT-${UUID.randomUUID().toString().uppercase()}",
                "caseId" to complaintCaseId,
                "actorId" to actor.id,
                "payloadJson" to objectMapper.writeValueAsString(sourceReference)
            )
        )
        return complaintCaseId
    }

    private fun findInteraction(interactionId: String): CallCenterInteractionDto {
        val interaction = findInteractionRecord(interactionId)
        return CallCenterInteractionDto(
            interactionId = interaction.interactionId,
            customerId = interaction.customerId,
            accountId = interaction.accountId,
            channel = interaction.channel,
            contactReasonCode = interaction.contactReasonCode,
            status = interaction.status,
            createdBy = interaction.createdBy,
            createdByRole = interaction.createdByRole,
            assignedTo = interaction.assignedTo,
            reason = interaction.reason,
            startedAt = interaction.startedAt,
            endedAt = interaction.endedAt,
            metadata = interaction.metadata,
            auditEventId = interaction.auditEventId,
            notes = notes(interactionId),
            aftercallTasks = tasks(interactionId),
            escalations = escalations(interactionId)
        )
    }

    private fun findInteractionRecord(interactionId: String): CallCenterInteractionRecord =
        queryInteraction(interactionSql("WHERE i.interaction_id = :interactionId"), mapOf("interactionId" to interactionId))

    private fun findInteractionRecordForUpdate(interactionId: String): CallCenterInteractionRecord =
        queryInteraction(interactionSql("WHERE i.interaction_id = :interactionId FOR UPDATE"), mapOf("interactionId" to interactionId))

    private fun queryInteraction(sql: String, params: Map<String, Any?>): CallCenterInteractionRecord =
        try {
            jdbc.queryForObject(sql, params, this::mapInteractionRecord)
                ?: throw WorkflowErrors.notFound("call-center interaction not found")
        } catch (_: EmptyResultDataAccessException) {
            throw WorkflowErrors.notFound("call-center interaction not found")
        }

    private fun notes(interactionId: String): List<CallCenterNoteDto> =
        jdbc.query(
            """
            SELECT note_id, interaction_id, customer_id, created_by, created_by_role,
                   note_body_redacted, redaction_applied, pii_pattern_count,
                   reason, audit_event_id, created_at
            FROM call_center_notes
            WHERE interaction_id = :interactionId
            ORDER BY created_at, note_id
            """.trimIndent(),
            mapOf("interactionId" to interactionId),
            this::mapNote
        )

    private fun tasks(interactionId: String): List<CallCenterAftercallTaskDto> =
        jdbc.query(
            """
            SELECT task_id, interaction_id, customer_id, task_type, status, assigned_to,
                   due_at, created_by, created_by_role, reason, audit_event_id,
                   metadata_json::text AS metadata_json, created_at
            FROM call_center_aftercall_tasks
            WHERE interaction_id = :interactionId
            ORDER BY created_at, task_id
            """.trimIndent(),
            mapOf("interactionId" to interactionId),
            this::mapTask
        )

    private fun escalations(interactionId: String): List<CallCenterEscalationDto> =
        jdbc.query(
            """
            SELECT escalation_id, interaction_id, customer_id, escalation_type, status,
                   complaint_case_id, requested_by, requested_by_role, reason,
                   audit_event_id, metadata_json::text AS metadata_json, created_at
            FROM call_center_escalations
            WHERE interaction_id = :interactionId
            ORDER BY created_at, escalation_id
            """.trimIndent(),
            mapOf("interactionId" to interactionId),
            this::mapEscalation
        )

    private fun findNote(noteId: String): CallCenterNoteDto =
        jdbc.queryForObject(
            """
            SELECT note_id, interaction_id, customer_id, created_by, created_by_role,
                   note_body_redacted, redaction_applied, pii_pattern_count,
                   reason, audit_event_id, created_at
            FROM call_center_notes
            WHERE note_id = :noteId
            """.trimIndent(),
            mapOf("noteId" to noteId),
            this::mapNote
        ) ?: throw WorkflowErrors.notFound("call-center note not found")

    private fun findTask(taskId: String): CallCenterAftercallTaskDto =
        jdbc.queryForObject(
            """
            SELECT task_id, interaction_id, customer_id, task_type, status, assigned_to,
                   due_at, created_by, created_by_role, reason, audit_event_id,
                   metadata_json::text AS metadata_json, created_at
            FROM call_center_aftercall_tasks
            WHERE task_id = :taskId
            """.trimIndent(),
            mapOf("taskId" to taskId),
            this::mapTask
        ) ?: throw WorkflowErrors.notFound("call-center aftercall task not found")

    private fun findEscalation(escalationId: String): CallCenterEscalationDto =
        jdbc.queryForObject(
            """
            SELECT escalation_id, interaction_id, customer_id, escalation_type, status,
                   complaint_case_id, requested_by, requested_by_role, reason,
                   audit_event_id, metadata_json::text AS metadata_json, created_at
            FROM call_center_escalations
            WHERE escalation_id = :escalationId
            """.trimIndent(),
            mapOf("escalationId" to escalationId),
            this::mapEscalation
        ) ?: throw WorkflowErrors.notFound("call-center escalation not found")

    private fun interactionSql(suffix: String): String =
        """
        SELECT i.interaction_id, i.customer_id, i.account_id, i.channel,
               i.contact_reason_code, i.status, i.created_by, i.created_by_role,
               i.assigned_to, i.reason, i.started_at, i.ended_at,
               i.metadata_json::text AS metadata_json, i.audit_event_id
        FROM call_center_interactions i
        $suffix
        """.trimIndent()

    private fun mapInteractionRecord(rs: ResultSet, rowNum: Int): CallCenterInteractionRecord =
        CallCenterInteractionRecord(
            interactionId = rs.getString("interaction_id"),
            customerId = rs.getString("customer_id"),
            accountId = rs.getString("account_id"),
            channel = rs.getString("channel"),
            contactReasonCode = rs.getString("contact_reason_code"),
            status = rs.getString("status"),
            createdBy = rs.getString("created_by"),
            createdByRole = rs.getString("created_by_role"),
            assignedTo = rs.getString("assigned_to"),
            reason = rs.getString("reason"),
            startedAt = rs.getObject("started_at", OffsetDateTime::class.java),
            endedAt = rs.getObject("ended_at", OffsetDateTime::class.java),
            metadata = readMap(rs.getString("metadata_json")),
            auditEventId = rs.getString("audit_event_id")
        )

    private fun mapCustomerSummary(rs: ResultSet, rowNum: Int): CallCenterCustomerSummaryDto =
        CallCenterCustomerSummaryDto(
            customerId = rs.getString("customer_id"),
            maskedName = maskName(rs.getString("customer_name")),
            maskedPhone = maskPhone(rs.getString("customer_phone")),
            customerGrade = rs.getString("customer_grade"),
            riskGrade = rs.getString("risk_grade")
        )

    private fun mapInteractionSummary(rs: ResultSet, rowNum: Int): CallCenterInteractionSummaryDto =
        CallCenterInteractionSummaryDto(
            interactionId = rs.getString("interaction_id"),
            customerId = rs.getString("customer_id"),
            maskedCustomerName = maskName(rs.getString("customer_name")),
            channel = rs.getString("channel"),
            contactReasonCode = rs.getString("contact_reason_code"),
            status = rs.getString("status"),
            assignedTo = rs.getString("assigned_to"),
            startedAt = rs.getObject("started_at", OffsetDateTime::class.java),
            endedAt = rs.getObject("ended_at", OffsetDateTime::class.java)
        )

    private fun mapNote(rs: ResultSet, rowNum: Int): CallCenterNoteDto =
        CallCenterNoteDto(
            noteId = rs.getString("note_id"),
            interactionId = rs.getString("interaction_id"),
            customerId = rs.getString("customer_id"),
            createdBy = rs.getString("created_by"),
            createdByRole = rs.getString("created_by_role"),
            noteBodyRedacted = rs.getString("note_body_redacted"),
            redactionApplied = rs.getBoolean("redaction_applied"),
            piiPatternCount = rs.getInt("pii_pattern_count"),
            reason = rs.getString("reason"),
            auditEventId = rs.getString("audit_event_id"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapTask(rs: ResultSet, rowNum: Int): CallCenterAftercallTaskDto =
        CallCenterAftercallTaskDto(
            taskId = rs.getString("task_id"),
            interactionId = rs.getString("interaction_id"),
            customerId = rs.getString("customer_id"),
            taskType = rs.getString("task_type"),
            status = rs.getString("status"),
            assignedTo = rs.getString("assigned_to"),
            dueAt = rs.getObject("due_at", OffsetDateTime::class.java),
            createdBy = rs.getString("created_by"),
            createdByRole = rs.getString("created_by_role"),
            reason = rs.getString("reason"),
            auditEventId = rs.getString("audit_event_id"),
            metadata = readMap(rs.getString("metadata_json")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun mapEscalation(rs: ResultSet, rowNum: Int): CallCenterEscalationDto =
        CallCenterEscalationDto(
            escalationId = rs.getString("escalation_id"),
            interactionId = rs.getString("interaction_id"),
            customerId = rs.getString("customer_id"),
            escalationType = rs.getString("escalation_type"),
            status = rs.getString("status"),
            complaintCaseId = rs.getString("complaint_case_id"),
            requestedBy = rs.getString("requested_by"),
            requestedByRole = rs.getString("requested_by_role"),
            reason = rs.getString("reason"),
            auditEventId = rs.getString("audit_event_id"),
            metadata = readMap(rs.getString("metadata_json")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun appendAudit(
        eventType: String,
        actor: CallCenterActor,
        screenId: String,
        interactionId: String?,
        customerId: String?,
        accountId: String?,
        reason: String,
        payload: Map<String, Any?>
    ): String =
        auditEvents.append(
            eventType = eventType,
            actorType = "STAFF",
            actorId = actor.id,
            actorRole = actor.role,
            screenId = screenId,
            businessReferenceId = interactionId,
            customerId = customerId,
            accountId = accountId,
            reason = reason,
            payload = payload
        )

    private fun appendAccessAudit(
        auditEventId: String,
        interactionId: String?,
        customerId: String,
        actor: CallCenterActor,
        screenId: String,
        actionType: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO call_center_access_audit (
              call_center_access_audit_id, interaction_id, customer_id, action_type,
              actor_id, actor_role, screen_id, reason, audit_event_id
            )
            VALUES (
              :accessAuditId, :interactionId, :customerId, :actionType,
              :actorId, :actorRole, :screenId, :reason, :auditEventId
            )
            """.trimIndent(),
            mapOf(
                "accessAuditId" to "CALL-AUD-${UUID.randomUUID().toString().uppercase()}",
                "interactionId" to interactionId,
                "customerId" to customerId,
                "actionType" to actionType,
                "actorId" to actor.id,
                "actorRole" to actor.role,
                "screenId" to screenId,
                "reason" to reason,
                "auditEventId" to auditEventId
            )
        )
    }

    private fun resolveActor(
        requestedBy: String?,
        requestedByRole: String?,
        allowedRoles: Set<String>,
        defaultRole: String,
        defaultActorId: String
    ): CallCenterActor {
        BankingLabAuthContext.requireActor(requestedBy, requestedByRole)
        val principal = BankingLabAuthContext.get()
        val role = requestedByRole?.trimToNull()
            ?: principal?.roles?.firstOrNull { it in allowedRoles }
            ?: defaultRole
        val actorId = requestedBy?.trimToNull() ?: principal?.subject ?: defaultActorId
        if (role !in allowedRoles) {
            throw WorkflowErrors.authorizationViolation("actor role cannot perform call-center workflow action")
        }
        return CallCenterActor(actorId, role)
    }

    private fun requireCustomer(customerId: String) {
        val exists = countRows("customers WHERE customer_id = :customerId", mapOf("customerId" to customerId))
        if (exists != 1) {
            throw WorkflowErrors.notFound("customer not found: $customerId")
        }
    }

    private fun requireCustomerAccount(customerId: String, accountId: String) {
        val exists = countRows(
            "accounts WHERE customer_id = :customerId AND account_id = :accountId",
            mapOf("customerId" to customerId, "accountId" to accountId)
        )
        if (exists != 1) {
            throw WorkflowErrors.validation("accountId does not belong to the call-center customer")
        }
    }

    private fun countRows(suffix: String, params: Map<String, Any?>): Int =
        jdbc.queryForObject("SELECT count(*) FROM $suffix", params, Int::class.java) ?: 0

    private fun requireOpenInteraction(interaction: CallCenterInteractionRecord) {
        if (interaction.status == "CLOSED") {
            throw WorkflowErrors.stateViolation("closed call-center interactions cannot be changed")
        }
    }

    private fun requireNotClosed(interaction: CallCenterInteractionRecord) =
        requireOpenInteraction(interaction)

    private fun requireReason(reason: String?, message: String): String =
        reason?.trim()?.takeIf { it.isNotBlank() } ?: throw WorkflowErrors.reasonRequired(message)

    private fun requireField(value: String?, field: String): String =
        value?.trim()?.takeIf { it.isNotBlank() } ?: throw WorkflowErrors.validation("$field is required")

    private fun normalize(value: String?, field: String, allowed: Set<String>): String {
        val normalized = requireField(value, field).uppercase()
        if (normalized !in allowed) {
            throw WorkflowErrors.validation("$field must be one of ${allowed.joinToString(", ")}")
        }
        return normalized
    }

    private fun syntheticMetadata(metadata: Map<String, Any?>?): Map<String, Any?> =
        (metadata ?: emptyMap()) + mapOf("syntheticOnly" to true)

    private fun readMap(payloadJson: String?): Map<String, Any?> =
        payloadJson?.let { objectMapper.readValue(it, MAP_TYPE) } ?: emptyMap()

    private fun redactNote(value: String): RedactionResult {
        var count = 0
        var redacted = value
        for (pattern in PII_PATTERNS) {
            val matches = pattern.findAll(redacted).count()
            if (matches > 0) {
                count += matches
                redacted = pattern.replace(redacted, "[REDACTED]")
            }
        }
        return RedactionResult(redacted = redacted, applied = count > 0, patternCount = count)
    }

    private fun maskName(name: String?): String {
        if (name.isNullOrBlank()) {
            return ""
        }
        if (name.length <= 2) {
            return "${name.first()}*"
        }
        return "${name.first()}${"*".repeat(name.length - 2)}${name.last()}"
    }

    private fun maskPhone(phone: String?): String? =
        phone?.replace(Regex("(\\d{3})-\\d{4}-(\\d{4})"), "$1-****-$2")

    private fun String?.trimToNull(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

    private data class CallCenterActor(val id: String, val role: String)

    private data class RedactionResult(
        val redacted: String,
        val applied: Boolean,
        val patternCount: Int
    )

    private data class CallCenterInteractionRecord(
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
        val auditEventId: String
    )

    private companion object {
        val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        val CHANNELS = setOf("PHONE", "CHAT", "EMAIL", "BRANCH", "WEB")
        val ESCALATION_TYPES = setOf("MANAGER", "COMPLAINT", "FDS", "AML")
        val COMMAND_ROLES = setOf("CALL_CENTER_AGENT", "CALL_CENTER_MANAGER", "BRANCH_STAFF", "BRANCH_MANAGER", "COMPLAINT_HANDLER", "COMPLIANCE_MANAGER")
        val ESCALATION_ROLES = setOf("CALL_CENTER_MANAGER", "BRANCH_MANAGER", "COMPLAINT_HANDLER", "COMPLIANCE_MANAGER")
        val READ_ROLES = COMMAND_ROLES + setOf("AUDITOR")
        val PII_PATTERNS = listOf(
            Regex("\\b\\d{3}-\\d{3,4}-\\d{4}\\b"),
            Regex("\\b\\d{6}-\\d{7}\\b"),
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            Regex("\\b\\d{2,6}-\\d{2,6}-\\d{2,8}\\b")
        )
    }
}

