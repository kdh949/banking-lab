package lab.banking.notification.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.OffsetDateTime
import lab.banking.notification.domain.NotificationTemplateChangeRequestRecord
import lab.banking.notification.domain.NotificationTemplateChangeStatus
import lab.banking.notification.domain.NotificationDeliveryRecord
import lab.banking.notification.domain.NotificationDeliveryStatus
import lab.banking.notification.domain.NotificationPreferenceRecord
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
            SELECT template_id, event_type, channel, version, status, body_template, provider_kind, synthetic_only, created_at
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
                version = rs.getInt("version"),
                status = rs.getString("status"),
                bodyTemplate = rs.getString("body_template"),
                providerKind = rs.getString("provider_kind"),
                syntheticOnly = rs.getBoolean("synthetic_only"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }.firstOrNull()

    fun listTemplates(eventType: String?, channel: String?): List<NotificationTemplateRecord> =
        jdbc.query(
            """
            SELECT template_id, event_type, channel, version, status, body_template, provider_kind, synthetic_only, created_at
            FROM notification_templates
            WHERE (:eventType IS NULL OR event_type = :eventType)
              AND (:channel IS NULL OR channel = :channel)
            ORDER BY event_type ASC, channel ASC, version DESC
            """.trimIndent(),
            mapOf("eventType" to eventType, "channel" to channel),
            this::mapTemplate
        )

    fun latestTemplateVersion(eventType: String, channel: String): Int =
        jdbc.queryForObject(
            """
            SELECT COALESCE(max(version), 0)
            FROM notification_templates
            WHERE event_type = :eventType
              AND channel = :channel
            """.trimIndent(),
            mapOf("eventType" to eventType, "channel" to channel),
            Int::class.java
        ) ?: 0

    fun insertTemplateChangeRequest(
        changeRequestId: String,
        eventType: String,
        channel: String,
        version: Int,
        bodyTemplate: String,
        providerKind: String,
        requestedBy: String,
        reason: String
    ) {
        jdbc.update(
            """
            INSERT INTO notification_template_change_requests (
              change_request_id, event_type, channel, requested_version, body_template,
              provider_kind, status, requested_by, request_reason, synthetic_only
            )
            VALUES (
              :changeRequestId, :eventType, :channel, :version, :bodyTemplate,
              :providerKind, 'PENDING', :requestedBy, :reason, true
            )
            """.trimIndent(),
            mapOf(
                "changeRequestId" to changeRequestId,
                "eventType" to eventType,
                "channel" to channel,
                "version" to version,
                "bodyTemplate" to bodyTemplate,
                "providerKind" to providerKind,
                "requestedBy" to requestedBy,
                "reason" to reason
            )
        )
    }

    fun findTemplateChangeRequest(changeRequestId: String): NotificationTemplateChangeRequestRecord? =
        findTemplateChangeRequestBySql(
            templateChangeRequestSql("WHERE change_request_id = :changeRequestId"),
            changeRequestId
        )

    fun findTemplateChangeRequestForUpdate(changeRequestId: String): NotificationTemplateChangeRequestRecord? =
        findTemplateChangeRequestBySql(
            templateChangeRequestSql("WHERE change_request_id = :changeRequestId FOR UPDATE"),
            changeRequestId
        )

    fun retireActiveTemplates(eventType: String, channel: String) {
        jdbc.update(
            """
            UPDATE notification_templates
            SET status = 'RETIRED'
            WHERE event_type = :eventType
              AND channel = :channel
              AND status = 'ACTIVE'
            """.trimIndent(),
            mapOf("eventType" to eventType, "channel" to channel)
        )
    }

    fun insertTemplate(
        templateId: String,
        eventType: String,
        channel: String,
        version: Int,
        bodyTemplate: String,
        providerKind: String
    ) {
        jdbc.update(
            """
            INSERT INTO notification_templates (
              template_id, event_type, channel, version, status, body_template, provider_kind, synthetic_only
            )
            VALUES (
              :templateId, :eventType, :channel, :version, 'ACTIVE', :bodyTemplate, :providerKind, true
            )
            """.trimIndent(),
            mapOf(
                "templateId" to templateId,
                "eventType" to eventType,
                "channel" to channel,
                "version" to version,
                "bodyTemplate" to bodyTemplate,
                "providerKind" to providerKind
            )
        )
    }

    fun approveTemplateChangeRequest(
        changeRequestId: String,
        reviewedBy: String,
        reviewedByRole: String,
        reason: String,
        approvedTemplateId: String
    ) {
        jdbc.update(
            """
            UPDATE notification_template_change_requests
            SET status = 'APPROVED',
                reviewed_by = :reviewedBy,
                reviewed_by_role = :reviewedByRole,
                reviewed_at = now(),
                review_reason = :reason,
                approved_template_id = :approvedTemplateId
            WHERE change_request_id = :changeRequestId
            """.trimIndent(),
            mapOf(
                "changeRequestId" to changeRequestId,
                "reviewedBy" to reviewedBy,
                "reviewedByRole" to reviewedByRole,
                "reason" to reason,
                "approvedTemplateId" to approvedTemplateId
            )
        )
    }

    fun rejectTemplateChangeRequest(
        changeRequestId: String,
        reviewedBy: String,
        reviewedByRole: String,
        reason: String
    ) {
        jdbc.update(
            """
            UPDATE notification_template_change_requests
            SET status = 'REJECTED',
                reviewed_by = :reviewedBy,
                reviewed_by_role = :reviewedByRole,
                reviewed_at = now(),
                review_reason = :reason
            WHERE change_request_id = :changeRequestId
            """.trimIndent(),
            mapOf(
                "changeRequestId" to changeRequestId,
                "reviewedBy" to reviewedBy,
                "reviewedByRole" to reviewedByRole,
                "reason" to reason
            )
        )
    }

    fun listPreferences(recipientId: String?, channel: String?): List<NotificationPreferenceRecord> =
        jdbc.query(
            """
            SELECT preference_id, recipient_id, channel, event_type, enabled,
                   requested_by, reason, synthetic_only, created_at, updated_at
            FROM notification_recipient_preferences
            WHERE (:recipientId IS NULL OR recipient_id = :recipientId)
              AND (:channel IS NULL OR channel = :channel)
            ORDER BY recipient_id ASC, channel ASC, event_type ASC
            """.trimIndent(),
            mapOf("recipientId" to recipientId, "channel" to channel),
            this::mapPreference
        )

    fun findPreference(
        recipientId: String,
        channel: String,
        eventType: String
    ): NotificationPreferenceRecord? =
        jdbc.query(
            """
            SELECT preference_id, recipient_id, channel, event_type, enabled,
                   requested_by, reason, synthetic_only, created_at, updated_at
            FROM notification_recipient_preferences
            WHERE recipient_id = :recipientId
              AND channel = :channel
              AND event_type IN (:eventType, '*')
            ORDER BY CASE WHEN event_type = :eventType THEN 0 ELSE 1 END
            LIMIT 1
            """.trimIndent(),
            mapOf("recipientId" to recipientId, "channel" to channel, "eventType" to eventType),
            this::mapPreference
        ).firstOrNull()

    fun upsertPreference(
        preferenceId: String,
        recipientId: String,
        channel: String,
        eventType: String,
        enabled: Boolean,
        requestedBy: String,
        reason: String
    ): NotificationPreferenceRecord =
        jdbc.query(
            """
            INSERT INTO notification_recipient_preferences (
              preference_id, recipient_id, channel, event_type, enabled,
              requested_by, reason, synthetic_only
            )
            VALUES (
              :preferenceId, :recipientId, :channel, :eventType, :enabled,
              :requestedBy, :reason, true
            )
            ON CONFLICT (recipient_id, channel, event_type)
            DO UPDATE SET
              enabled = EXCLUDED.enabled,
              requested_by = EXCLUDED.requested_by,
              reason = EXCLUDED.reason,
              updated_at = now()
            RETURNING preference_id, recipient_id, channel, event_type, enabled,
                      requested_by, reason, synthetic_only, created_at, updated_at
            """.trimIndent(),
            mapOf(
                "preferenceId" to preferenceId,
                "recipientId" to recipientId,
                "channel" to channel,
                "eventType" to eventType,
                "enabled" to enabled,
                "requestedBy" to requestedBy,
                "reason" to reason
            ),
            this::mapPreference
        ).single()

    fun insertAccessAudit(
        auditEventId: String,
        action: String,
        actorId: String,
        reason: String,
        targetType: String,
        targetId: String,
        details: Map<String, Any?>
    ) {
        jdbc.update(
            """
            INSERT INTO notification_access_audit_events (
              audit_event_id, action, actor_id, reason, target_type, target_id, details_json, synthetic_only
            )
            VALUES (
              :auditEventId, :action, :actorId, :reason, :targetType, :targetId,
              CAST(:detailsJson AS jsonb), true
            )
            """.trimIndent(),
            mapOf(
                "auditEventId" to auditEventId,
                "action" to action,
                "actorId" to actorId,
                "reason" to reason,
                "targetType" to targetType,
                "targetId" to targetId,
                "detailsJson" to objectMapper.writeValueAsString(details)
            )
        )
    }

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

    fun insertSuppression(
        suppressionId: String,
        consumerName: String,
        sourceEventId: String,
        eventType: String,
        recipientId: String,
        channel: String,
        preferenceId: String,
        reason: String,
        maskedPayload: Map<String, Any?>,
        requestedBy: String
    ) {
        jdbc.update(
            """
            INSERT INTO notification_suppressed_events (
              suppression_id, consumer_name, source_event_id, event_type, recipient_id,
              channel, preference_id, reason, masked_payload_json, requested_by, synthetic_only
            )
            VALUES (
              :suppressionId, :consumerName, :sourceEventId, :eventType, :recipientId,
              :channel, :preferenceId, :reason, CAST(:maskedPayloadJson AS jsonb), :requestedBy, true
            )
            ON CONFLICT (consumer_name, source_event_id) DO NOTHING
            """.trimIndent(),
            mapOf(
                "suppressionId" to suppressionId,
                "consumerName" to consumerName,
                "sourceEventId" to sourceEventId,
                "eventType" to eventType,
                "recipientId" to recipientId,
                "channel" to channel,
                "preferenceId" to preferenceId,
                "reason" to reason,
                "maskedPayloadJson" to objectMapper.writeValueAsString(maskedPayload),
                "requestedBy" to requestedBy
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

    private fun findTemplateChangeRequestBySql(
        sql: String,
        changeRequestId: String
    ): NotificationTemplateChangeRequestRecord? =
        jdbc.query(sql, mapOf("changeRequestId" to changeRequestId), this::mapTemplateChangeRequest).firstOrNull()

    private fun mapPreference(rs: ResultSet, rowNum: Int): NotificationPreferenceRecord =
        NotificationPreferenceRecord(
            preferenceId = rs.getString("preference_id"),
            recipientId = rs.getString("recipient_id"),
            channel = rs.getString("channel"),
            eventType = rs.getString("event_type"),
            enabled = rs.getBoolean("enabled"),
            requestedBy = rs.getString("requested_by"),
            reason = rs.getString("reason"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java)
        )

    private fun mapTemplate(rs: ResultSet, rowNum: Int): NotificationTemplateRecord =
        NotificationTemplateRecord(
            templateId = rs.getString("template_id"),
            eventType = rs.getString("event_type"),
            channel = rs.getString("channel"),
            version = rs.getInt("version"),
            status = rs.getString("status"),
            bodyTemplate = rs.getString("body_template"),
            providerKind = rs.getString("provider_kind"),
            syntheticOnly = rs.getBoolean("synthetic_only"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
        )

    private fun templateChangeRequestSql(suffix: String): String =
        """
        SELECT change_request_id, event_type, channel, requested_version, body_template,
               provider_kind, status, requested_by, request_reason, requested_at,
               reviewed_by, reviewed_by_role, reviewed_at, review_reason,
               approved_template_id, synthetic_only
        FROM notification_template_change_requests
        $suffix
        """.trimIndent()

    private fun mapTemplateChangeRequest(rs: ResultSet, rowNum: Int): NotificationTemplateChangeRequestRecord =
        NotificationTemplateChangeRequestRecord(
            changeRequestId = rs.getString("change_request_id"),
            eventType = rs.getString("event_type"),
            channel = rs.getString("channel"),
            requestedVersion = rs.getInt("requested_version"),
            bodyTemplate = rs.getString("body_template"),
            providerKind = rs.getString("provider_kind"),
            status = NotificationTemplateChangeStatus.valueOf(rs.getString("status")),
            requestedBy = rs.getString("requested_by"),
            requestReason = rs.getString("request_reason"),
            requestedAt = rs.getObject("requested_at", OffsetDateTime::class.java),
            reviewedBy = rs.getString("reviewed_by"),
            reviewedByRole = rs.getString("reviewed_by_role"),
            reviewedAt = rs.getObject("reviewed_at", OffsetDateTime::class.java),
            reviewReason = rs.getString("review_reason"),
            approvedTemplateId = rs.getString("approved_template_id"),
            syntheticOnly = rs.getBoolean("synthetic_only")
        )

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
