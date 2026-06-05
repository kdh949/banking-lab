package lab.banking.notification.domain

import java.time.OffsetDateTime
import org.springframework.http.HttpStatus

enum class NotificationDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
    DEAD_LETTER
}

data class ConsumeNotificationEventRequest(
    val sourceEventId: String,
    val eventType: String,
    val recipientId: String,
    val channel: String = "SMS",
    val payload: Map<String, Any?>,
    val requestedBy: String = "notification-event-consumer"
)

data class RecordNotificationFailureRequest(
    val errorMessage: String,
    val requestedBy: String,
    val reason: String,
    val deadLetterThreshold: Int = 3
)

data class MarkNotificationDeliveredRequest(
    val requestedBy: String,
    val reason: String
)

data class NotificationDeliveryResponse(
    val items: List<NotificationDeliveryDto>,
    val replayed: Boolean
)

data class NotificationDeliveryDto(
    val deliveryRequestId: String,
    val sourceEventId: String,
    val eventType: String,
    val recipientId: String,
    val channel: String,
    val providerKind: String,
    val status: NotificationDeliveryStatus,
    val maskedMessage: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class NotificationTemplateRecord(
    val templateId: String,
    val eventType: String,
    val channel: String,
    val bodyTemplate: String,
    val providerKind: String,
    val syntheticOnly: Boolean
)

data class NotificationDeliveryRecord(
    val deliveryRequestId: String,
    val sourceEventId: String,
    val eventType: String,
    val recipientId: String,
    val channel: String,
    val providerKind: String,
    val status: NotificationDeliveryStatus,
    val maskedMessage: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

class NotificationDomainException(
    val code: String,
    val status: HttpStatus,
    val invariant: String? = null,
    val policy: String? = null,
    override val message: String,
    val causeText: String,
    val fix: String,
    val details: Map<String, Any?>? = null
) : RuntimeException(message)
