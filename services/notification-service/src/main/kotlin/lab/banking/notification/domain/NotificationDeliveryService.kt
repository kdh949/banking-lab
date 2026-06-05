package lab.banking.notification.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID
import lab.banking.notification.persistence.NotificationRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationDeliveryService(
    private val repository: NotificationRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun consumeEvent(request: ConsumeNotificationEventRequest): NotificationDeliveryResponse {
        validateConsume(request)
        val payloadHash = sha256(objectMapper.writeValueAsString(request.payload))
        val inserted = repository.recordInboxEvent(
            consumerName = "notification-service",
            sourceEventId = request.sourceEventId,
            eventType = request.eventType,
            payloadHash = payloadHash
        )
        if (!inserted) {
            return NotificationDeliveryResponse(
                items = repository.findDeliveriesBySourceEvent(request.sourceEventId).map { it.toDto() },
                replayed = true
            )
        }

        val template = repository.findTemplate(request.eventType, request.channel)
            ?: throw notificationError(
                code = "NOTIFICATION_TEMPLATE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "notification template was not found",
                cause = "The synthetic event has no approved notification template for the requested channel.",
                fix = "Seed a synthetic notification template for the eventType and channel before consuming the event.",
                details = mapOf("eventType" to request.eventType, "channel" to request.channel)
            )
        if (!template.syntheticOnly || !template.providerKind.startsWith("SYNTHETIC_")) {
            throw notificationError(
                code = "NOTIFICATION_REAL_PROVIDER_FORBIDDEN",
                status = HttpStatus.FORBIDDEN,
                policy = "SYNTHETIC_ONLY_NOTIFICATION_PROVIDER",
                message = "real notification provider configuration is forbidden",
                cause = "Notification Service must not send SMS, email, push, or chat messages through real providers.",
                fix = "Keep templates bound to SYNTHETIC_*_SINK provider kinds only."
            )
        }

        val maskedPayload = maskPayload(request.payload)
        val maskedMessage = render(template.bodyTemplate, maskedPayload)
        val deliveryRequestId = deliveryRequestId()
        repository.insertDeliveryRequest(
            deliveryRequestId = deliveryRequestId,
            sourceEventId = request.sourceEventId,
            eventType = request.eventType,
            recipientId = request.recipientId,
            channel = request.channel,
            providerKind = template.providerKind,
            templateId = template.templateId,
            maskedPayload = maskedPayload,
            maskedMessage = maskedMessage
        )
        repository.insertAttempt(
            attemptId = attemptId(),
            deliveryRequestId = deliveryRequestId,
            status = NotificationDeliveryStatus.PENDING,
            providerKind = template.providerKind,
            requestedBy = request.requestedBy,
            reason = "Synthetic notification delivery request"
        )

        return NotificationDeliveryResponse(
            items = repository.findDeliveriesBySourceEvent(request.sourceEventId).map { it.toDto() },
            replayed = false
        )
    }

    fun delivery(deliveryRequestId: String): NotificationDeliveryDto =
        (repository.findDelivery(deliveryRequestId) ?: throw notFound(deliveryRequestId)).toDto()

    @Transactional
    fun recordFailure(
        deliveryRequestId: String,
        request: RecordNotificationFailureRequest
    ): NotificationDeliveryDto {
        validateOperatorCommand(request.requestedBy, request.reason)
        requireNonBlank(request.errorMessage, "errorMessage")
        if (request.deadLetterThreshold <= 0) {
            throw notificationError(
                code = "NOTIFICATION_DEAD_LETTER_THRESHOLD_INVALID",
                status = HttpStatus.BAD_REQUEST,
                invariant = "dead-letter threshold must be positive",
                message = "dead-letter threshold must be positive",
                cause = "Notification retry policy cannot evaluate a non-positive threshold.",
                fix = "Submit deadLetterThreshold greater than zero."
            )
        }
        val current = repository.findDeliveryForUpdate(deliveryRequestId) ?: throw notFound(deliveryRequestId)
        if (current.status == NotificationDeliveryStatus.DELIVERED || current.status == NotificationDeliveryStatus.DEAD_LETTER) {
            throw invalidState(deliveryRequestId, current.status, "terminal notification delivery cannot record another failure")
        }

        val nextFailureCount = repository.countAttempts(deliveryRequestId, NotificationDeliveryStatus.FAILED) + 1
        val nextStatus = if (nextFailureCount >= request.deadLetterThreshold) {
            NotificationDeliveryStatus.DEAD_LETTER
        } else {
            NotificationDeliveryStatus.FAILED
        }
        repository.updateDeliveryStatus(deliveryRequestId, nextStatus)
        repository.insertAttempt(
            attemptId = attemptId(),
            deliveryRequestId = deliveryRequestId,
            status = nextStatus,
            providerKind = current.providerKind,
            requestedBy = request.requestedBy,
            reason = request.reason,
            errorMessage = request.errorMessage
        )
        if (nextStatus == NotificationDeliveryStatus.DEAD_LETTER) {
            repository.insertDeadLetter(
                deadLetterId = deadLetterId(),
                deliveryRequestId = deliveryRequestId,
                reason = request.errorMessage
            )
        }
        return delivery(deliveryRequestId)
    }

    @Transactional
    fun markDelivered(
        deliveryRequestId: String,
        request: MarkNotificationDeliveredRequest
    ): NotificationDeliveryDto {
        validateOperatorCommand(request.requestedBy, request.reason)
        val current = repository.findDeliveryForUpdate(deliveryRequestId) ?: throw notFound(deliveryRequestId)
        if (current.status == NotificationDeliveryStatus.DEAD_LETTER) {
            throw invalidState(deliveryRequestId, current.status, "dead-letter notification delivery cannot be marked delivered")
        }
        if (current.status != NotificationDeliveryStatus.DELIVERED) {
            repository.updateDeliveryStatus(deliveryRequestId, NotificationDeliveryStatus.DELIVERED)
            repository.insertAttempt(
                attemptId = attemptId(),
                deliveryRequestId = deliveryRequestId,
                status = NotificationDeliveryStatus.DELIVERED,
                providerKind = current.providerKind,
                requestedBy = request.requestedBy,
                reason = request.reason
            )
        }
        return delivery(deliveryRequestId)
    }

    private fun validateConsume(request: ConsumeNotificationEventRequest) {
        requireNonBlank(request.sourceEventId, "sourceEventId")
        requireNonBlank(request.eventType, "eventType")
        requireNonBlank(request.recipientId, "recipientId")
        requireNonBlank(request.channel, "channel")
        requireNonBlank(request.requestedBy, "requestedBy")
        if (request.payload.isEmpty()) {
            throw notificationError(
                code = "NOTIFICATION_PAYLOAD_REQUIRED",
                status = HttpStatus.BAD_REQUEST,
                message = "notification payload is required",
                cause = "Notification rendering needs a bounded synthetic payload.",
                fix = "Pass event payload fields that can be masked and rendered."
            )
        }
    }

    private fun validateOperatorCommand(requestedBy: String, reason: String) {
        requireNonBlank(requestedBy, "requestedBy")
        requireNonBlank(reason, "reason")
    }

    private fun requireNonBlank(value: String, field: String) {
        if (value.isBlank()) {
            throw notificationError(
                code = "NOTIFICATION_REQUIRED_FIELD_MISSING",
                status = HttpStatus.BAD_REQUEST,
                message = "$field is required",
                cause = "Notification Service requires stable event, actor, channel, and reason fields.",
                fix = "Populate $field before retrying the notification command.",
                details = mapOf("field" to field)
            )
        }
    }

    private fun render(template: String, payload: Map<String, Any?>): String {
        var rendered = template
        payload.forEach { (key, value) ->
            rendered = rendered.replace("{$key}", value?.toString() ?: "")
        }
        return rendered
    }

    private fun maskPayload(payload: Map<String, Any?>): Map<String, Any?> =
        payload.mapValues { (key, value) ->
            val normalizedKey = key.lowercase()
            when {
                value == null -> null
                "accountno" in normalizedKey ||
                    "account_no" in normalizedKey ||
                    "accountid" in normalizedKey ||
                    "account_id" in normalizedKey ||
                    "accountnumber" in normalizedKey ||
                    "account_number" in normalizedKey -> maskAccount(value.toString())
                "phone" in normalizedKey || "email" in normalizedKey || "name" in normalizedKey -> "[MASKED]"
                "raw" in normalizedKey || "pii" in normalizedKey -> "[MASKED]"
                else -> value
            }
        }

    private fun maskAccount(value: String): String =
        if (value.length <= 4) {
            "****"
        } else {
            "${value.take(4)}***${value.takeLast(4)}"
        }

    private fun NotificationDeliveryRecord.toDto(): NotificationDeliveryDto =
        NotificationDeliveryDto(
            deliveryRequestId = deliveryRequestId,
            sourceEventId = sourceEventId,
            eventType = eventType,
            recipientId = recipientId,
            channel = channel,
            providerKind = providerKind,
            status = status,
            maskedMessage = maskedMessage,
            syntheticOnly = syntheticOnly,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun notFound(deliveryRequestId: String): NotificationDomainException =
        notificationError(
            code = "NOTIFICATION_DELIVERY_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "notification delivery was not found",
            cause = "No durable notification delivery request exists for the supplied id.",
            fix = "Check the delivery request id returned by the event consumption response.",
            details = mapOf("deliveryRequestId" to deliveryRequestId)
        )

    private fun invalidState(
        deliveryRequestId: String,
        status: NotificationDeliveryStatus,
        message: String
    ): NotificationDomainException =
        notificationError(
            code = "NOTIFICATION_STATE_TRANSITION_REJECTED",
            status = HttpStatus.CONFLICT,
            invariant = "notification status transitions are durable and auditable",
            message = message,
            cause = "Notification Service rejected an unsafe delivery status transition.",
            fix = "Use the current delivery status and submit the next allowed command.",
            details = mapOf("deliveryRequestId" to deliveryRequestId, "status" to status.name)
        )

    private fun notificationError(
        code: String,
        status: HttpStatus,
        invariant: String? = null,
        policy: String? = null,
        message: String,
        cause: String,
        fix: String,
        details: Map<String, Any?>? = null
    ): NotificationDomainException =
        NotificationDomainException(
            code = code,
            status = status,
            invariant = invariant,
            policy = policy,
            message = message,
            causeText = cause,
            fix = fix,
            details = details
        )

    private fun deliveryRequestId(): String = "NDL-${UUID.randomUUID().toString().uppercase()}"

    private fun attemptId(): String = "NAT-${UUID.randomUUID().toString().uppercase()}"

    private fun deadLetterId(): String = "NDLQ-${UUID.randomUUID().toString().uppercase()}"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
