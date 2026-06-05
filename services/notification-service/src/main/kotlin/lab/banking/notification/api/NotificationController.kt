package lab.banking.notification.api

import jakarta.servlet.http.HttpServletRequest
import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.ApproveNotificationTemplateChangeRequest
import lab.banking.notification.domain.CreateNotificationTemplateChangeRequest
import lab.banking.notification.domain.MarkNotificationDeliveredRequest
import lab.banking.notification.domain.NotificationDeliveryDto
import lab.banking.notification.domain.NotificationDeliveryResponse
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.NotificationDomainException
import lab.banking.notification.domain.NotificationPreferenceDto
import lab.banking.notification.domain.NotificationPreferenceService
import lab.banking.notification.domain.NotificationTemplateAdminService
import lab.banking.notification.domain.NotificationTemplateChangeRequestDto
import lab.banking.notification.domain.NotificationTemplateDto
import lab.banking.notification.domain.RecordNotificationFailureRequest
import lab.banking.notification.domain.RejectNotificationTemplateChangeRequest
import lab.banking.notification.domain.UpsertCustomerNotificationPreferenceRequest
import lab.banking.notification.domain.UpsertNotificationPreferenceRequest
import lab.banking.notification.security.NotificationAuthorizationFilter
import lab.banking.notification.security.NotificationPrincipal
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

    @GetMapping("/customers/{customerId}/deliveries")
    fun customerDeliveryHistory(
        @PathVariable customerId: String,
        @RequestParam(required = false) sourceEventId: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) limit: Int?,
        servletRequest: HttpServletRequest
    ): List<NotificationDeliveryDto> =
        notificationDeliveryService.customerDeliveryHistory(
            customerId = customerId,
            sourceEventId = sourceEventId,
            eventType = eventType,
            channel = channel,
            status = status,
            limit = limit,
            principal = notificationPrincipal(servletRequest)
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

    @GetMapping("/customers/{customerId}/preferences")
    fun customerPreferences(
        @PathVariable customerId: String,
        @RequestParam(required = false) channel: String?,
        servletRequest: HttpServletRequest
    ): List<NotificationPreferenceDto> =
        notificationPreferenceService.customerPreferences(
            customerId = customerId,
            channel = channel,
            principal = notificationPrincipal(servletRequest)
        )

    @PutMapping("/customers/{customerId}/preferences")
    fun upsertCustomerPreference(
        @PathVariable customerId: String,
        @RequestBody request: UpsertCustomerNotificationPreferenceRequest,
        servletRequest: HttpServletRequest
    ): NotificationPreferenceDto =
        notificationPreferenceService.upsertCustomerPreference(
            customerId = customerId,
            request = request,
            principal = notificationPrincipal(servletRequest)
        )

    private fun notificationPrincipal(request: HttpServletRequest): NotificationPrincipal =
        request.getAttribute(NotificationAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? NotificationPrincipal
            ?: throw NotificationDomainException(
                code = "NOTIFICATION_PRINCIPAL_CONTEXT_MISSING",
                status = org.springframework.http.HttpStatus.UNAUTHORIZED,
                policy = "NOTIFICATION_RBAC_ROUTE_POLICY",
                message = "notification principal context is missing",
                causeText = "The authorization filter did not attach a decoded notification principal to the request.",
                fix = "Call the customer notification route with a valid synthetic CUSTOMER bearer token."
            )
}
