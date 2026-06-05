package lab.banking.notification.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import lab.banking.notification.domain.NotificationDeliveryRecord
import lab.banking.notification.domain.NotificationDeliveryStatus
import lab.banking.notification.domain.NotificationTemplateRecord
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class NotificationRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun recordInboxEvent(
        consumerName: String,
        sourceEventId: String,
        eventType: String,
        payloadHash: String
    ): Boolean {
        val inserted = jdbc.update(
            """
            INSERT INTO notification_inbox_events (
              consumer_name, source_event_id, event_type, payload_hash
            )
            VALUES (:consumerName, :sourceEventId, :eventType, :payloadHash)
            ON CONFLICT (consumer_name, source_event_id) DO NOTHING
            """.trimIndent(),
            mapOf(
                "consumerName" to consumerName,
                "sourceEventId" to sourceEventId,
                "eventType" to eventType,
                "payloadHash" to payloadHash
            )
        )
        return inserted == 1
    }

    fun findTemplate(eventType: String, channel: String): NotificationTemplateRecord? =
        jdbc.query(
            """
            SELECT template_id, event_type, channel, body_template, provider_kind, synthetic_only
            FROM notification_templates
            WHERE event_type = :eventType
              AND channel = :channel
              AND status = 'ACTIVE'
            ORDER BY version DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("eventType" to eventType, "channel" to channel)
        ) { rs, _ ->
            NotificationTemplateRecord(
                templateId = rs.getString("template_id"),
                eventType = rs.getString("event_type"),
                channel = rs.getString("channel"),
                bodyTemplate = rs.getString("body_template"),
                providerKind = rs.getString("provider_kind"),
                syntheticOnly = rs.getBoolean("synthetic_only")
            )
        }.firstOrNull()

    fun insertDeliveryRequest(
        deliveryRequestId: String,
        sourceEventId: String,
        eventType: String,
        recipientId: String,
        channel: String,
        providerKind: String,
        templateId: String,
        maskedPayload: Map<String, Any?>,
        maskedMessage: String
    ) {
        jdbc.update(
            """
            INSERT INTO notification_delivery_requests (
              delivery_request_id, source_event_id, event_type, recipient_id, channel,
              provider_kind, template_id, masked_payload_json, masked_message, status, synthetic_only
            )
            VALUES (
              :deliveryRequestId, :sourceEventId, :eventType, :recipientId, :channel,
              :providerKind, :templateId, CAST(:maskedPayloadJson AS jsonb), :maskedMessage, 'PENDING', true
            )
            """.trimIndent(),
            mapOf(
                "deliveryRequestId" to deliveryRequestId,
                "sourceEventId" to sourceEventId,
                "eventType" to eventType,
                "recipientId" to recipientId,
                "channel" to channel,
                "providerKind" to providerKind,
                "templateId" to templateId,
                "maskedPayloadJson" to objectMapper.writeValueAsString(maskedPayload),
                "maskedMessage" to maskedMessage
            )
        )
    }

    fun insertAttempt(
        attemptId: String,
        deliveryRequestId: String,
        status: NotificationDeliveryStatus,
        providerKind: String,
        requestedBy: String,
        reason: String,
        errorMessage: String? = null
    ) {
        jdbc.update(
            """
            INSERT INTO notification_delivery_attempts (
              delivery_attempt_id, delivery_request_id, attempt_no, provider_kind, status,
              requested_by, reason, error_message, synthetic_only, completed_at
            )
            VALUES (
              :attemptId,
              :deliveryRequestId,
              COALESCE((SELECT max(attempt_no) + 1 FROM notification_delivery_attempts WHERE delivery_request_id = :deliveryRequestId), 1),
              :providerKind,
              :status,
              :requestedBy,
              :reason,
              :errorMessage,
              true,
              CASE WHEN :status = 'PENDING' THEN NULL ELSE now() END
            )
            """.trimIndent(),
            mapOf(
                "attemptId" to attemptId,
                "deliveryRequestId" to deliveryRequestId,
                "providerKind" to providerKind,
                "status" to status.name,
                "requestedBy" to requestedBy,
                "reason" to reason,
                "errorMessage" to errorMessage
            )
        )
    }

    fun findDeliveriesBySourceEvent(sourceEventId: String): List<NotificationDeliveryRecord> =
        jdbc.query(
            """
            SELECT delivery_request_id, source_event_id, event_type, recipient_id, channel,
                   provider_kind, status, masked_message, synthetic_only, created_at, updated_at
            FROM notification_delivery_requests
            WHERE source_event_id = :sourceEventId
            ORDER BY created_at ASC, delivery_request_id ASC
            """.trimIndent(),
            mapOf("sourceEventId" to sourceEventId),
            this::mapDelivery
        )

    fun findDelivery(deliveryRequestId: String): NotificationDeliveryRecord? =
        findDeliveryBySql(
            """
            SELECT delivery_request_id, source_event_id, event_type, recipient_id, channel,
                   provider_kind, status, masked_message, synthetic_only, created_at, updated_at
            FROM notification_delivery_requests
            WHERE delivery_request_id = :deliveryRequestId
            """.trimIndent(),
            deliveryRequestId
        )

    fun findDeliveryForUpdate(deliveryRequestId: String): NotificationDeliveryRecord? =
        findDeliveryBySql(
            """
            SELECT delivery_request_id, source_event_id, event_type, recipient_id, channel,
                   provider_kind, status, masked_message, synthetic_only, created_at, updated_at
            FROM notification_delivery_requests
            WHERE delivery_request_id = :deliveryRequestId
            FOR UPDATE
            """.trimIndent(),
            deliveryRequestId
        )

    fun updateDeliveryStatus(deliveryRequestId: String, status: NotificationDeliveryStatus) {
        jdbc.update(
            """
            UPDATE notification_delivery_requests
            SET status = :status,
                updated_at = now()
            WHERE delivery_request_id = :deliveryRequestId
            """.trimIndent(),
            mapOf("deliveryRequestId" to deliveryRequestId, "status" to status.name)
        )
    }

    fun countAttempts(deliveryRequestId: String, status: NotificationDeliveryStatus): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM notification_delivery_attempts
            WHERE delivery_request_id = :deliveryRequestId
              AND status = :status
            """.trimIndent(),
            mapOf("deliveryRequestId" to deliveryRequestId, "status" to status.name),
            Int::class.java
        ) ?: 0

    fun insertDeadLetter(deadLetterId: String, deliveryRequestId: String, reason: String) {
        jdbc.update(
            """
            INSERT INTO notification_dead_letters (
              dead_letter_id, delivery_request_id, reason
            )
            VALUES (:deadLetterId, :deliveryRequestId, :reason)
            """.trimIndent(),
            mapOf(
                "deadLetterId" to deadLetterId,
                "deliveryRequestId" to deliveryRequestId,
                "reason" to reason
            )
        )
    }

    private fun findDeliveryBySql(sql: String, deliveryRequestId: String): NotificationDeliveryRecord? =
        jdbc.query(sql, mapOf("deliveryRequestId" to deliveryRequestId), this::mapDelivery).firstOrNull()

    private fun mapDelivery(rs: ResultSet, rowNum: Int): NotificationDeliveryRecord =
        NotificationDeliveryRecord(
            deliveryRequestId = rs.getString("delivery_request_id"),
            sourceEventId = rs.getString("source_event_id"),
            eventType = rs.getString("event_type"),
            recipientId = rs.getString("recipient_id"),
            channel = rs.getString("channel"),
            providerKind = rs.getString("provider_kind"),
            status = NotificationDeliveryStatus.valueOf(rs.getString("status")),
            maskedMessage = rs.getString("masked_message"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )
}
