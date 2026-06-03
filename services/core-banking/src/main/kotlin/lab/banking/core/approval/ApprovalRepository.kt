package lab.banking.core.approval

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.audit.AuditEventAppender
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ApprovalRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val auditEvents: AuditEventAppender
) {
    fun nextApprovalId(): String =
        "APR-${UUID.randomUUID().toString().uppercase()}"

    fun insertPending(approvalId: String, command: SubmitApprovalCommand, auditEventId: String): OperatorApproval {
        jdbc.update(
            """
            INSERT INTO operator_approvals (
              approval_id, business_type, business_reference_id, requested_by,
              request_reason, before_snapshot_json, after_snapshot_json, status, audit_event_id
            )
            VALUES (
              :approvalId, :businessType, :businessReferenceId, :requestedBy,
              :requestReason, CAST(:beforeSnapshotJson AS jsonb), CAST(:afterSnapshotJson AS jsonb),
              :status, :auditEventId
            )
            """.trimIndent(),
            mapOf(
                "approvalId" to approvalId,
                "businessType" to command.businessType,
                "businessReferenceId" to command.businessReferenceId,
                "requestedBy" to command.requestedBy,
                "requestReason" to command.requestReason.orEmpty(),
                "beforeSnapshotJson" to command.beforeSnapshot?.let(objectMapper::writeValueAsString),
                "afterSnapshotJson" to objectMapper.writeValueAsString(command.afterSnapshot ?: emptyMap<String, Any?>()),
                "status" to ApprovalStatus.PENDING.name,
                "auditEventId" to auditEventId
            )
        )
        return find(approvalId)
    }

    fun find(approvalId: String): OperatorApproval =
        jdbc.queryForObject(
            approvalSql("WHERE approval_id = :approvalId"),
            mapOf("approvalId" to approvalId),
            this::mapApproval
        ) ?: throw WorkflowErrors.notFound("approval not found: $approvalId")

    fun findForUpdate(approvalId: String): OperatorApproval =
        jdbc.queryForObject(
            approvalSql("WHERE approval_id = :approvalId FOR UPDATE"),
            mapOf("approvalId" to approvalId),
            this::mapApproval
        ) ?: throw WorkflowErrors.notFound("approval not found: $approvalId")

    fun approve(approvalId: String, command: ApproveApprovalCommand) {
        jdbc.update(
            """
            UPDATE operator_approvals
            SET status = :status,
                approved_by = :approvedBy,
                approved_at = now()
            WHERE approval_id = :approvalId
            """.trimIndent(),
            mapOf(
                "approvalId" to approvalId,
                "status" to ApprovalStatus.APPROVED.name,
                "approvedBy" to command.approvedBy
            )
        )
    }

    fun reject(approvalId: String, command: RejectApprovalCommand) {
        jdbc.update(
            """
            UPDATE operator_approvals
            SET status = :status,
                rejected_by = :rejectedBy,
                rejected_at = now(),
                reject_reason = :rejectReason
            WHERE approval_id = :approvalId
            """.trimIndent(),
            mapOf(
                "approvalId" to approvalId,
                "status" to ApprovalStatus.REJECTED.name,
                "rejectedBy" to command.rejectedBy,
                "rejectReason" to command.rejectReason
            )
        )
    }

    fun list(): List<OperatorApproval> =
        jdbc.query(
            approvalSql("ORDER BY requested_at, approval_id"),
            emptyMap<String, Any?>(),
            this::mapApproval
        )

    fun appendAudit(
        eventType: String,
        actorId: String,
        actorRole: String,
        screenId: String?,
        businessReferenceId: String,
        reason: String?,
        payload: Map<String, Any?>
    ): ApprovalAuditEvent {
        val eventId = auditEvents.append(
            eventType = eventType,
            actorType = "OPERATOR",
            actorId = actorId,
            actorRole = actorRole,
            screenId = screenId,
            businessReferenceId = businessReferenceId,
            reason = reason,
            payload = payload
        )
        return auditEvent(eventId)
    }

    fun auditEvents(): List<ApprovalAuditEvent> =
        jdbc.query(
            auditSql("ORDER BY created_at, audit_event_id"),
            emptyMap<String, Any?>(),
            this::mapAudit
        )

    private fun auditEvent(eventId: String): ApprovalAuditEvent =
        jdbc.queryForObject(
            auditSql("WHERE audit_event_id = :eventId"),
            mapOf("eventId" to eventId),
            this::mapAudit
        ) ?: throw WorkflowErrors.notFound("audit event not found: $eventId")

    private fun approvalSql(suffix: String): String =
        """
        SELECT approval_id, business_type, business_reference_id, requested_by, requested_at,
               request_reason, before_snapshot_json::text AS before_snapshot_json,
               after_snapshot_json::text AS after_snapshot_json, status, approved_by, approved_at,
               rejected_by, rejected_at, reject_reason, audit_event_id
        FROM operator_approvals
        $suffix
        """.trimIndent()

    private fun auditSql(suffix: String): String =
        """
        SELECT audit_event_id, event_type, actor_id, actor_role, screen_id,
               business_reference_id, reason, payload_json::text AS payload_json, created_at
        FROM audit_events
        $suffix
        """.trimIndent()

    private fun mapApproval(rs: ResultSet, rowNum: Int): OperatorApproval =
        OperatorApproval(
            approvalId = rs.getString("approval_id"),
            businessType = rs.getString("business_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            requestedBy = rs.getString("requested_by"),
            requestedAt = rs.getObject("requested_at", OffsetDateTime::class.java),
            requestReason = rs.getString("request_reason"),
            beforeSnapshot = readNullableMap(rs.getString("before_snapshot_json")),
            afterSnapshot = readNullableMap(rs.getString("after_snapshot_json")),
            status = ApprovalStatus.valueOf(rs.getString("status")),
            approvedBy = rs.getString("approved_by"),
            approvedAt = rs.getObject("approved_at", OffsetDateTime::class.java),
            rejectedBy = rs.getString("rejected_by"),
            rejectedAt = rs.getObject("rejected_at", OffsetDateTime::class.java),
            rejectReason = rs.getString("reject_reason"),
            auditEventId = rs.getString("audit_event_id")
        )

    private fun mapAudit(rs: ResultSet, rowNum: Int): ApprovalAuditEvent =
        ApprovalAuditEvent(
            auditEventId = rs.getString("audit_event_id"),
            eventType = rs.getString("event_type"),
            actorId = rs.getString("actor_id"),
            actorRole = rs.getString("actor_role"),
            screenId = rs.getString("screen_id"),
            businessReferenceId = rs.getString("business_reference_id"),
            reason = rs.getString("reason"),
            payload = readNullableMap(rs.getString("payload_json")) ?: emptyMap(),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    @Suppress("UNCHECKED_CAST")
    private fun readNullableMap(payloadJson: String?): Map<String, Any?>? =
        payloadJson?.let { objectMapper.readValue(it, Map::class.java) as Map<String, Any?> }
}
