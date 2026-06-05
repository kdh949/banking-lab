package lab.banking.notification.domain

import java.time.OffsetDateTime
import org.springframework.http.HttpStatus

enum class NotificationDeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
    DEAD_LETTER
}

enum class NotificationTemplateChangeStatus {
    PENDING,
    APPROVED,
    REJECTED
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

data class CreateNotificationTemplateChangeRequest(
    val eventType: String,
    val channel: String,
    val version: Int,
    val bodyTemplate: String,
    val providerKind: String,
    val requestedBy: String,
    val reason: String,
    val syntheticOnly: Boolean = true
)

data class ApproveNotificationTemplateChangeRequest(
    val approvedBy: String,
    val approvedByRole: String,
    val reason: String
)

data class RejectNotificationTemplateChangeRequest(
    val rejectedBy: String,
    val rejectedByRole: String,
    val reason: String
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

data class NotificationTemplateDto(
    val templateId: String,
    val eventType: String,
    val channel: String,
    val version: Int,
    val status: String,
    val bodyTemplate: String,
    val providerKind: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class NotificationTemplateChangeRequestDto(
    val changeRequestId: String,
    val eventType: String,
    val channel: String,
    val requestedVersion: Int,
    val bodyTemplate: String,
    val providerKind: String,
    val status: NotificationTemplateChangeStatus,
    val requestedBy: String,
    val requestReason: String,
    val requestedAt: OffsetDateTime,
    val reviewedBy: String?,
    val reviewedByRole: String?,
    val reviewedAt: OffsetDateTime?,
    val reviewReason: String?,
    val approvedTemplateId: String?,
    val syntheticOnly: Boolean
)

data class NotificationTemplateRecord(
    val templateId: String,
    val eventType: String,
    val channel: String,
    val version: Int,
    val status: String,
    val bodyTemplate: String,
    val providerKind: String,
    val syntheticOnly: Boolean,
    val createdAt: OffsetDateTime
)

data class NotificationTemplateChangeRequestRecord(
    val changeRequestId: String,
    val eventType: String,
    val channel: String,
    val requestedVersion: Int,
    val bodyTemplate: String,
    val providerKind: String,
    val status: NotificationTemplateChangeStatus,
    val requestedBy: String,
    val requestReason: String,
    val requestedAt: OffsetDateTime,
    val reviewedBy: String?,
    val reviewedByRole: String?,
    val reviewedAt: OffsetDateTime?,
    val reviewReason: String?,
    val approvedTemplateId: String?,
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
