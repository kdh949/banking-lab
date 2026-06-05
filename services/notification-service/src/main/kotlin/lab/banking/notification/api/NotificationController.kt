package lab.banking.notification.api

import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.ApproveNotificationTemplateChangeRequest
import lab.banking.notification.domain.CreateNotificationTemplateChangeRequest
import lab.banking.notification.domain.MarkNotificationDeliveredRequest
import lab.banking.notification.domain.NotificationDeliveryDto
import lab.banking.notification.domain.NotificationDeliveryResponse
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.NotificationPreferenceDto
import lab.banking.notification.domain.NotificationPreferenceService
import lab.banking.notification.domain.NotificationTemplateAdminService
import lab.banking.notification.domain.NotificationTemplateChangeRequestDto
import lab.banking.notification.domain.NotificationTemplateDto
import lab.banking.notification.domain.RecordNotificationFailureRequest
import lab.banking.notification.domain.RejectNotificationTemplateChangeRequest
import lab.banking.notification.domain.UpsertNotificationPreferenceRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PutMapping

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationDeliveryService: NotificationDeliveryService,
    private val notificationTemplateAdminService: NotificationTemplateAdminService,
    private val notificationPreferenceService: NotificationPreferenceService
) {
    @PostMapping("/events")
    fun consumeEvent(@RequestBody request: ConsumeNotificationEventRequest): NotificationDeliveryResponse =
        notificationDeliveryService.consumeEvent(request)

    @GetMapping("/deliveries/{deliveryRequestId}")
    fun delivery(@PathVariable deliveryRequestId: String): NotificationDeliveryDto =
        notificationDeliveryService.delivery(deliveryRequestId)

    @GetMapping("/deliveries")
    fun deliveryHistory(
        @RequestParam(required = false) recipientId: String?,
        @RequestParam(required = false) sourceEventId: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam requestedBy: String,
        @RequestParam reason: String,
        @RequestParam(required = false) limit: Int?
    ): List<NotificationDeliveryDto> =
        notificationDeliveryService.deliveryHistory(
            recipientId = recipientId,
            sourceEventId = sourceEventId,
            eventType = eventType,
            channel = channel,
            status = status,
            requestedBy = requestedBy,
            reason = reason,
            limit = limit
        )

    @PostMapping("/deliveries/{deliveryRequestId}/failures")
    fun recordFailure(
        @PathVariable deliveryRequestId: String,
        @RequestBody request: RecordNotificationFailureRequest
    ): NotificationDeliveryDto =
        notificationDeliveryService.recordFailure(deliveryRequestId, request)

    @PostMapping("/deliveries/{deliveryRequestId}/delivered")
    fun markDelivered(
        @PathVariable deliveryRequestId: String,
        @RequestBody request: MarkNotificationDeliveredRequest
    ): NotificationDeliveryDto =
        notificationDeliveryService.markDelivered(deliveryRequestId, request)

    @GetMapping("/templates")
    fun templates(
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) channel: String?
    ): List<NotificationTemplateDto> =
        notificationTemplateAdminService.templates(eventType, channel)

    @PostMapping("/templates/change-requests")
    fun createTemplateChangeRequest(
        @RequestBody request: CreateNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto =
        notificationTemplateAdminService.createChangeRequest(request)

    @GetMapping("/templates/change-requests/{changeRequestId}")
    fun templateChangeRequest(@PathVariable changeRequestId: String): NotificationTemplateChangeRequestDto =
        notificationTemplateAdminService.changeRequest(changeRequestId)

    @PostMapping("/templates/change-requests/{changeRequestId}/approve")
    fun approveTemplateChangeRequest(
        @PathVariable changeRequestId: String,
        @RequestBody request: ApproveNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto =
        notificationTemplateAdminService.approveChangeRequest(changeRequestId, request)

    @PostMapping("/templates/change-requests/{changeRequestId}/reject")
    fun rejectTemplateChangeRequest(
        @PathVariable changeRequestId: String,
        @RequestBody request: RejectNotificationTemplateChangeRequest
    ): NotificationTemplateChangeRequestDto =
        notificationTemplateAdminService.rejectChangeRequest(changeRequestId, request)

    @GetMapping("/preferences")
    fun preferences(
        @RequestParam(required = false) recipientId: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam requestedBy: String,
        @RequestParam reason: String
    ): List<NotificationPreferenceDto> =
        notificationPreferenceService.preferences(recipientId, channel, requestedBy, reason)

    @PutMapping("/preferences")
    fun upsertPreference(
        @RequestBody request: UpsertNotificationPreferenceRequest
    ): NotificationPreferenceDto =
        notificationPreferenceService.upsertPreference(request)
}
