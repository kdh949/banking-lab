package lab.banking.notification.api

import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.MarkNotificationDeliveredRequest
import lab.banking.notification.domain.NotificationDeliveryDto
import lab.banking.notification.domain.NotificationDeliveryResponse
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.RecordNotificationFailureRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(private val notificationDeliveryService: NotificationDeliveryService) {
    @PostMapping("/events")
    fun consumeEvent(@RequestBody request: ConsumeNotificationEventRequest): NotificationDeliveryResponse =
        notificationDeliveryService.consumeEvent(request)

    @GetMapping("/deliveries/{deliveryRequestId}")
    fun delivery(@PathVariable deliveryRequestId: String): NotificationDeliveryDto =
        notificationDeliveryService.delivery(deliveryRequestId)

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
}
