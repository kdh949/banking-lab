package lab.banking.core.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OutboxEventRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun insertPending(command: CreateOutboxEventCommand): OutboxEventRecord {
        val eventId = "OBX-${UUID.randomUUID().toString().uppercase()}"
        jdbc.update(
            """
            INSERT INTO outbox_events (
              outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
              payload_json, headers_json, status
            )
            VALUES (
              :eventId, :aggregateType, :aggregateId, :eventType, :idempotencyKey,
              CAST(:payloadJson AS jsonb), CAST(:headersJson AS jsonb), 'PENDING'
            )
            """.trimIndent(),
            mapOf(
                "eventId" to eventId,
                "aggregateType" to command.aggregateType,
                "aggregateId" to command.aggregateId,
                "eventType" to command.eventType,
                "idempotencyKey" to command.idempotencyKey,
                "payloadJson" to objectMapper.writeValueAsString(command.payload),
                "headersJson" to objectMapper.writeValueAsString(command.headers)
            )
        )
        return find(eventId)
    }

    fun find(outboxEventId: String): OutboxEventRecord =
        jdbc.queryForObject(
            """
            SELECT outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                   payload_json, headers_json, status, retry_count, next_retry_at,
                   created_at, published_at, error_message
            FROM outbox_events
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf("outboxEventId" to outboxEventId),
            this::mapOutboxEvent
        ) ?: throw IllegalArgumentException("outbox event not found: $outboxEventId")

    fun findForUpdate(outboxEventId: String): OutboxEventRecord =
        jdbc.queryForObject(
            """
            SELECT outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                   payload_json, headers_json, status, retry_count, next_retry_at,
                   created_at, published_at, error_message
            FROM outbox_events
            WHERE outbox_event_id = :outboxEventId
            FOR UPDATE
            """.trimIndent(),
            mapOf("outboxEventId" to outboxEventId),
            this::mapOutboxEvent
        ) ?: throw IllegalArgumentException("outbox event not found: $outboxEventId")

    fun findNextPublishableForUpdate(): OutboxEventRecord? =
        jdbc.query(
            """
            SELECT outbox_event_id, aggregate_type, aggregate_id, event_type, idempotency_key,
                   payload_json, headers_json, status, retry_count, next_retry_at,
                   created_at, published_at, error_message
            FROM outbox_events
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= now()))
            ORDER BY created_at ASC, outbox_event_id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            emptyMap<String, Any?>(),
            this::mapOutboxEvent
        ).firstOrNull()

    fun markPublished(outboxEventId: String) {
        jdbc.update(
            """
            UPDATE outbox_events
            SET status = 'PUBLISHED',
                published_at = now(),
                error_message = NULL,
                next_retry_at = NULL
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf("outboxEventId" to outboxEventId)
        )
    }

    fun markFailed(outboxEventId: String, retryCount: Int, errorMessage: String, retryDelaySeconds: Long) {
        jdbc.update(
            """
            UPDATE outbox_events
            SET status = 'FAILED',
                retry_count = :retryCount,
                error_message = :errorMessage,
                next_retry_at = now() + (:retryDelaySeconds * interval '1 second')
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf(
                "outboxEventId" to outboxEventId,
                "retryCount" to retryCount,
                "errorMessage" to errorMessage,
                "retryDelaySeconds" to retryDelaySeconds
            )
        )
    }

    fun markDeadLetter(outboxEventId: String, retryCount: Int, errorMessage: String) {
        jdbc.update(
            """
            UPDATE outbox_events
            SET status = 'DEAD_LETTER',
                retry_count = :retryCount,
                error_message = :errorMessage,
                next_retry_at = NULL
            WHERE outbox_event_id = :outboxEventId
            """.trimIndent(),
            mapOf(
                "outboxEventId" to outboxEventId,
                "retryCount" to retryCount,
                "errorMessage" to errorMessage
            )
        )
    }

    fun recordInboxProcessed(
        consumerName: String,
        sourceEventId: String,
        eventType: String,
        payload: Map<String, Any?>
    ): InboxRecordResult {
        val inserted = jdbc.update(
            """
            INSERT INTO inbox_events (
              inbox_event_id, consumer_name, source_event_id, event_type, payload_hash
            )
            VALUES (
              :inboxEventId, :consumerName, :sourceEventId, :eventType, :payloadHash
            )
            ON CONFLICT (consumer_name, source_event_id) DO NOTHING
            """.trimIndent(),
            mapOf(
                "inboxEventId" to "INB-${UUID.randomUUID().toString().uppercase()}",
                "consumerName" to consumerName,
                "sourceEventId" to sourceEventId,
                "eventType" to eventType,
                "payloadHash" to sha256(objectMapper.writeValueAsString(payload))
            )
        )
        return InboxRecordResult(
            consumerName = consumerName,
            sourceEventId = sourceEventId,
            processedNow = inserted == 1
        )
    }

    fun countInboxRows(consumerName: String, sourceEventId: String): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM inbox_events
            WHERE consumer_name = :consumerName
              AND source_event_id = :sourceEventId
            """.trimIndent(),
            mapOf("consumerName" to consumerName, "sourceEventId" to sourceEventId),
            Int::class.java
        ) ?: 0

    private fun mapOutboxEvent(rs: ResultSet, rowNum: Int): OutboxEventRecord =
        OutboxEventRecord(
            outboxEventId = rs.getString("outbox_event_id"),
            aggregateType = rs.getString("aggregate_type"),
            aggregateId = rs.getString("aggregate_id"),
            eventType = rs.getString("event_type"),
            idempotencyKey = rs.getString("idempotency_key"),
            payload = readMap(rs.getString("payload_json")),
            headers = readMap(rs.getString("headers_json")),
            status = OutboxEventStatus.valueOf(rs.getString("status")),
            retryCount = rs.getInt("retry_count"),
            nextRetryAt = rs.getObject("next_retry_at", OffsetDateTime::class.java),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            publishedAt = rs.getObject("published_at", OffsetDateTime::class.java),
            errorMessage = rs.getString("error_message")
        )

    @Suppress("UNCHECKED_CAST")
    private fun readMap(payloadJson: String): Map<String, Any?> =
        objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any?>

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
