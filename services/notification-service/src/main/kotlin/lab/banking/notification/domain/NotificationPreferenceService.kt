package lab.banking.notification.domain

import java.util.UUID
import lab.banking.notification.persistence.NotificationRepository
import lab.banking.notification.security.NotificationPrincipal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationPreferenceService(
    private val repository: NotificationRepository
) {
    private val supportedChannels = setOf("SMS", "EMAIL", "PUSH", "CHAT")

    @Transactional
    fun preferences(
        recipientId: String?,
        channel: String?,
        requestedBy: String,
        reason: String
    ): List<NotificationPreferenceDto> {
        requireNonBlank(requestedBy, "requestedBy")
        requireReason(reason)
        val normalizedRecipientId = recipientId?.trim()?.takeIf { it.isNotBlank() }
        val normalizedChannel = channel?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        repository.insertAccessAudit(
            auditEventId = auditEventId(),
            action = "NOTIFICATION_PREFERENCE_VIEW",
            actorId = requestedBy.trim(),
            reason = reason.trim(),
            targetType = "NOTIFICATION_PREFERENCE",
            targetId = normalizedRecipientId ?: "ALL",
            details = mapOf("channel" to normalizedChannel, "syntheticOnly" to true)
        )
        return repository
            .listPreferences(
                recipientId = normalizedRecipientId,
                channel = normalizedChannel
            )
            .map { it.toDto() }
    }

    @Transactional
    fun upsertPreference(request: UpsertNotificationPreferenceRequest): NotificationPreferenceDto {
        validate(request)
        return repository.upsertPreference(
            preferenceId = preferenceId(),
            recipientId = request.recipientId.trim(),
            channel = request.channel.trim().uppercase(),
            eventType = normalizeEventType(request.eventType),
            enabled = request.enabled,
            requestedBy = request.requestedBy.trim(),
            reason = request.reason.trim()
        ).toDto()
    }

    @Transactional
    fun customerPreferences(
        customerId: String,
        channel: String?,
        principal: NotificationPrincipal
    ): List<NotificationPreferenceDto> {
        validateCustomerScope(customerId, principal)
        val normalizedCustomerId = customerId.trim()
        val normalizedChannel = channel?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        repository.insertAccessAudit(
            auditEventId = auditEventId(),
            action = "NOTIFICATION_CUSTOMER_PREFERENCE_VIEW",
            actorId = principal.subject,
            reason = "Customer self-service notification preference review",
            targetType = "NOTIFICATION_PREFERENCE",
            targetId = normalizedCustomerId,
            details = mapOf("channel" to normalizedChannel, "selfService" to true, "syntheticOnly" to true)
        )
        return repository
            .listPreferences(
                recipientId = normalizedCustomerId,
                channel = normalizedChannel
            )
            .map { it.toDto() }
    }

    @Transactional
    fun upsertCustomerPreference(
        customerId: String,
        request: UpsertCustomerNotificationPreferenceRequest,
        principal: NotificationPrincipal
    ): NotificationPreferenceDto {
        validateCustomerScope(customerId, principal)
        return upsertPreference(
            UpsertNotificationPreferenceRequest(
                recipientId = customerId.trim(),
                channel = request.channel,
                eventType = request.eventType,
                enabled = request.enabled,
                requestedBy = principal.subject,
                reason = "Customer self-service notification preference update",
                syntheticOnly = request.syntheticOnly
            )
        )
    }

    private fun validate(request: UpsertNotificationPreferenceRequest) {
        requireNonBlank(request.recipientId, "recipientId")
        requireNonBlank(request.channel, "channel")
        requireNonBlank(request.requestedBy, "requestedBy")
        requireReason(request.reason)
        if (!request.syntheticOnly) {
            throw notificationError(
                code = "NOTIFICATION_REAL_PROVIDER_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_NOTIFICATION_PROVIDER",
                message = "notification preferences must stay synthetic-only",
                cause = "Notification Service must not configure real provider behavior.",
                fix = "Set syntheticOnly=true and keep provider handling inside synthetic sinks."
            )
        }
        val channel = request.channel.trim().uppercase()
        if (channel !in supportedChannels) {
            throw notificationError(
                code = "NOTIFICATION_PREFERENCE_CHANNEL_INVALID",
                status = HttpStatus.BAD_REQUEST,
                message = "notification preference channel is not supported",
                cause = "Notification Service supports only synthetic SMS, EMAIL, PUSH, and CHAT channels.",
                fix = "Use one of ${supportedChannels.sorted().joinToString(", ")}.",
                details = mapOf("channel" to request.channel)
            )
        }
    }

    private fun validateCustomerScope(customerId: String, principal: NotificationPrincipal) {
        requireNonBlank(customerId, "customerId")
        val normalizedCustomerId = customerId.trim()
        if (!principal.roles.contains("CUSTOMER") || principal.customerId != normalizedCustomerId) {
            throw notificationError(
                code = "NOTIFICATION_CUSTOMER_SCOPE_VIOLATION",
                status = HttpStatus.FORBIDDEN,
                policy = "CUSTOMER_OWNED_NOTIFICATION_PREFERENCES",
                message = "customer may access only their own notification preferences",
                cause = "The notification preference self-service route was called with a customerId outside the token scope.",
                fix = "Retry with a CUSTOMER token whose customerId claim matches the path customerId.",
                details = mapOf(
                    "pathCustomerId" to normalizedCustomerId,
                    "tokenCustomerId" to principal.customerId,
                    "actor" to principal.subject,
                    "roles" to principal.roles.sorted(),
                    "syntheticOnly" to true
                )
            )
        }
    }

    private fun requireReason(reason: String) {
        if (reason.isBlank()) {
            throw notificationError(
                code = "POLICY_REASON_REQUIRED",
                status = HttpStatus.BAD_REQUEST,
                policy = "REASON_REQUIRED_FOR_NOTIFICATION_PREFERENCE",
                message = "reason is required",
                cause = "Notification preference changes affect event delivery and must carry a business reason.",
                fix = "Provide a concise synthetic business reason before retrying."
            )
        }
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw notificationError(
                code = "NOTIFICATION_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Notification preference administration requires stable target, actor, and reason fields.",
                fix = "Populate $field before retrying the notification preference command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun normalizeEventType(eventType: String?): String =
        eventType?.trim()?.takeIf { it.isNotBlank() } ?: "*"

    private fun NotificationPreferenceRecord.toDto(): NotificationPreferenceDto =
        NotificationPreferenceDto(
            preferenceId = preferenceId,
            recipientId = recipientId,
            channel = channel,
            eventType = eventType,
            enabled = enabled,
            requestedBy = requestedBy,
            reason = reason,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun notificationError(
        code: String,
        status: HttpStatus,
        policy: String? = null,
        message: String,
        cause: String,
        fix: String,
        details: Map<String, Any?>? = null
    ): NotificationDomainException =
        NotificationDomainException(
            code = code,
            status = status,
            policy = policy,
            message = message,
            causeText = cause,
            fix = fix,
            details = details
        )

    private fun preferenceId(): String = "NPF-${UUID.randomUUID().toString().uppercase()}"

    private fun auditEventId(): String = "NAUD-${UUID.randomUUID().toString().uppercase()}"
}
