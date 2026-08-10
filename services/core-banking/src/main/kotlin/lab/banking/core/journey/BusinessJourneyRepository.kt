package lab.banking.core.journey

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import lab.banking.core.workflow.WorkflowErrors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BusinessJourneyRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun insertHeldTransfer(command: HeldTransferJourneyCommand): String {
        val journeyId = "JRN-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO business_journeys (
              journey_id, journey_type, customer_id, status,
              primary_reference_type, primary_reference_id
            ) VALUES (
              :journeyId, 'HELD_TRANSFER', :customerId, 'HELD',
              'CUSTOMER_TRANSFER_RESULT', :transferResultId
            )
            """.trimIndent(),
            mapOf(
                "journeyId" to journeyId,
                "customerId" to command.customerId,
                "transferResultId" to command.transferResultId
            )
        )
        jdbc.update(
            """
            UPDATE customer_transfer_results
            SET journey_id = :journeyId
            WHERE result_id = :transferResultId
            """.trimIndent(),
            mapOf("journeyId" to journeyId, "transferResultId" to command.transferResultId)
        )
        jdbc.update(
            """
            UPDATE fds_cases
            SET journey_id = :journeyId
            WHERE fds_case_id = :fdsCaseId
            """.trimIndent(),
            mapOf("journeyId" to journeyId, "fdsCaseId" to command.fdsCaseId)
        )
        return journeyId
    }

    fun addReference(journeyId: String, referenceType: String, referenceId: String): Boolean =
        jdbc.update(
            """
            INSERT INTO business_journey_references (journey_id, reference_type, reference_id)
            VALUES (:journeyId, :referenceType, :referenceId)
            ON CONFLICT (journey_id, reference_type, reference_id) DO NOTHING
            """.trimIndent(),
            mapOf("journeyId" to journeyId, "referenceType" to referenceType, "referenceId" to referenceId)
        ) > 0

    fun updateStatus(journeyId: String, status: String) {
        val updated = jdbc.update(
            """
            UPDATE business_journeys
            SET status = :status, updated_at = now()
            WHERE journey_id = :journeyId
            """.trimIndent(),
            mapOf("journeyId" to journeyId, "status" to status)
        )
        if (updated != 1) {
            throw WorkflowErrors.notFound("business journey not found: $journeyId")
        }
    }

    fun appendEvent(
        journeyId: String,
        eventType: String,
        status: String,
        sourceReferenceType: String?,
        sourceReferenceId: String?,
        actorId: String?,
        actorRole: String?,
        reason: String?,
        traceId: String?,
        requestId: String?,
        payload: Map<String, Any?> = emptyMap()
    ) {
        jdbc.update(
            """
            INSERT INTO business_journey_events (
              journey_event_id, journey_id, event_type, journey_status,
              source_reference_type, source_reference_id, actor_id, actor_role,
              reason, trace_id, request_id, payload_json
            ) VALUES (
              :eventId, :journeyId, :eventType, :status,
              :sourceReferenceType, :sourceReferenceId, :actorId, :actorRole,
              :reason, :traceId, :requestId, CAST(:payloadJson AS jsonb)
            )
            """.trimIndent(),
            mapOf(
                "eventId" to "JEV-${UUID.randomUUID().toString().uppercase()}",
                "journeyId" to journeyId,
                "eventType" to eventType,
                "status" to status,
                "sourceReferenceType" to sourceReferenceType,
                "sourceReferenceId" to sourceReferenceId,
                "actorId" to actorId,
                "actorRole" to actorRole,
                "reason" to reason,
                "traceId" to traceId,
                "requestId" to requestId,
                "payloadJson" to objectMapper.writeValueAsString(payload + ("syntheticOnly" to true))
            )
        )
    }

    fun journeyIdForReference(referenceType: String, referenceId: String): String? =
        jdbc.query(
            """
            SELECT journey_id
            FROM business_journey_references
            WHERE reference_type = :referenceType AND reference_id = :referenceId
            """.trimIndent(),
            mapOf("referenceType" to referenceType, "referenceId" to referenceId)
        ) { rs, _ -> rs.getString("journey_id") }.firstOrNull()

    fun find(journeyId: String): BusinessJourneyDto {
        val journey = jdbc.query(
            """
            SELECT journey_id, journey_type, customer_id, status,
                   primary_reference_type, primary_reference_id,
                   created_at, updated_at, synthetic_only
            FROM business_journeys
            WHERE journey_id = :journeyId
            """.trimIndent(),
            mapOf("journeyId" to journeyId),
            this::mapJourneyRow
        ).firstOrNull() ?: throw WorkflowErrors.notFound("business journey not found: $journeyId")
        return journey.copy(
            references = references(journeyId),
            events = events(journeyId)
        )
    }

    private fun references(journeyId: String): List<JourneyReferenceDto> =
        jdbc.query(
            """
            SELECT reference_type, reference_id, created_at
            FROM business_journey_references
            WHERE journey_id = :journeyId
            ORDER BY created_at, reference_type, reference_id
            """.trimIndent(),
            mapOf("journeyId" to journeyId)
        ) { rs, _ ->
            JourneyReferenceDto(
                referenceType = rs.getString("reference_type"),
                referenceId = rs.getString("reference_id"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }

    private fun events(journeyId: String): List<JourneyEventDto> =
        jdbc.query(
            """
            SELECT journey_event_id, event_sequence, event_type, journey_status,
                   source_reference_type, source_reference_id, actor_role, reason,
                   trace_id, request_id, created_at
            FROM business_journey_events
            WHERE journey_id = :journeyId
            ORDER BY event_sequence
            """.trimIndent(),
            mapOf("journeyId" to journeyId)
        ) { rs, _ ->
            JourneyEventDto(
                eventId = rs.getString("journey_event_id"),
                sequence = rs.getLong("event_sequence"),
                eventType = rs.getString("event_type"),
                status = rs.getString("journey_status"),
                sourceReferenceType = rs.getString("source_reference_type"),
                sourceReferenceId = rs.getString("source_reference_id"),
                actorRole = rs.getString("actor_role"),
                reason = rs.getString("reason"),
                traceId = rs.getString("trace_id"),
                requestId = rs.getString("request_id"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }

    private fun mapJourneyRow(rs: ResultSet, rowNum: Int): BusinessJourneyDto =
        BusinessJourneyDto(
            journeyId = rs.getString("journey_id"),
            journeyType = rs.getString("journey_type"),
            customerId = rs.getString("customer_id"),
            status = rs.getString("status"),
            primaryReferenceType = rs.getString("primary_reference_type"),
            primaryReferenceId = rs.getString("primary_reference_id"),
            references = emptyList(),
            events = emptyList(),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )
}
