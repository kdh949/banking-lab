package lab.banking.core.workflow

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class DurableWorkflowRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun insert(command: StartWorkflowCommand): WorkflowInstanceRecord {
        val workflowInstanceId = "WFI-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO workflow_instances (
              workflow_instance_id, workflow_type, business_reference_id, status, started_by
            )
            VALUES (
              :workflowInstanceId, :workflowType, :businessReferenceId, :status, :startedBy
            )
            """.trimIndent(),
            mapOf(
                "workflowInstanceId" to workflowInstanceId,
                "workflowType" to command.workflowType,
                "businessReferenceId" to command.businessReferenceId,
                "status" to WorkflowInstanceStatus.STARTED.name,
                "startedBy" to command.startedBy
            )
        )
        appendEvent(
            workflowInstanceId = workflowInstanceId,
            eventType = command.eventType,
            actorId = command.startedBy,
            payload = command.payload
        )
        return find(workflowInstanceId)
    }

    fun find(workflowInstanceId: String): WorkflowInstanceRecord =
        jdbc.queryForObject(
            """
            SELECT workflow_instance_id, workflow_type, business_reference_id,
                   temporal_workflow_id, temporal_run_id, status, started_by, started_at, updated_at
            FROM workflow_instances
            WHERE workflow_instance_id = :workflowInstanceId
            """.trimIndent(),
            mapOf("workflowInstanceId" to workflowInstanceId),
            this::mapInstance
        ) ?: throw WorkflowErrors.notFound("workflow instance not found: $workflowInstanceId")

    fun findForUpdate(workflowInstanceId: String): WorkflowInstanceRecord =
        jdbc.queryForObject(
            """
            SELECT workflow_instance_id, workflow_type, business_reference_id,
                   temporal_workflow_id, temporal_run_id, status, started_by, started_at, updated_at
            FROM workflow_instances
            WHERE workflow_instance_id = :workflowInstanceId
            FOR UPDATE
            """.trimIndent(),
            mapOf("workflowInstanceId" to workflowInstanceId),
            this::mapInstance
        ) ?: throw WorkflowErrors.notFound("workflow instance not found: $workflowInstanceId")

    fun updateStatus(workflowInstanceId: String, status: WorkflowInstanceStatus) {
        jdbc.update(
            """
            UPDATE workflow_instances
            SET status = :status,
                updated_at = now()
            WHERE workflow_instance_id = :workflowInstanceId
            """.trimIndent(),
            mapOf("workflowInstanceId" to workflowInstanceId, "status" to status.name)
        )
    }

    fun appendEvent(
        workflowInstanceId: String,
        eventType: String,
        actorId: String?,
        payload: Map<String, Any?>
    ): WorkflowEventRecord {
        val eventId = "WFE-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO workflow_events (
              workflow_event_id, workflow_instance_id, event_type, actor_id, payload_json
            )
            VALUES (
              :eventId, :workflowInstanceId, :eventType, :actorId, CAST(:payloadJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "eventId" to eventId,
                "workflowInstanceId" to workflowInstanceId,
                "eventType" to eventType,
                "actorId" to actorId,
                "payloadJson" to objectMapper.writeValueAsString(payload)
            )
        )
        return events(workflowInstanceId).first { it.workflowEventId == eventId }
    }

    fun events(workflowInstanceId: String): List<WorkflowEventRecord> =
        jdbc.query(
            """
            SELECT workflow_event_id, workflow_instance_id, event_type, actor_id, payload_json, created_at
            FROM workflow_events
            WHERE workflow_instance_id = :workflowInstanceId
            ORDER BY created_at, workflow_event_id
            """.trimIndent(),
            mapOf("workflowInstanceId" to workflowInstanceId),
            this::mapEvent
        )

    private fun mapInstance(rs: ResultSet, rowNum: Int): WorkflowInstanceRecord =
        WorkflowInstanceRecord(
            workflowInstanceId = rs.getString("workflow_instance_id"),
            workflowType = rs.getString("workflow_type"),
            businessReferenceId = rs.getString("business_reference_id"),
            temporalWorkflowId = rs.getString("temporal_workflow_id"),
            temporalRunId = rs.getString("temporal_run_id"),
            status = WorkflowInstanceStatus.valueOf(rs.getString("status")),
            startedBy = rs.getString("started_by"),
            startedAt = rs.getObject("started_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapEvent(rs: ResultSet, rowNum: Int): WorkflowEventRecord =
        WorkflowEventRecord(
            workflowEventId = rs.getString("workflow_event_id"),
            workflowInstanceId = rs.getString("workflow_instance_id"),
            eventType = rs.getString("event_type"),
            actorId = rs.getString("actor_id"),
            payload = readPayload(rs.getString("payload_json")),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    @Suppress("UNCHECKED_CAST")
    private fun readPayload(payloadJson: String): Map<String, Any?> =
        objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any?>
}
