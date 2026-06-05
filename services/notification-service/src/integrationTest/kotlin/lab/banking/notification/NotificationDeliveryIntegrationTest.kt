package lab.banking.notification

import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.MarkNotificationDeliveredRequest
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.NotificationDeliveryStatus
import lab.banking.notification.domain.NotificationDomainException
import lab.banking.notification.domain.RecordNotificationFailureRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
class NotificationDeliveryIntegrationTest {
    @Autowired
    lateinit var notificationDeliveryService: NotificationDeliveryService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              notification_dead_letters,
              notification_delivery_attempts,
              notification_delivery_requests,
              notification_inbox_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `event consumption creates masked synthetic delivery and is idempotent`() {
        val first = notificationDeliveryService.consumeEvent(sampleEvent("OBX-NOTIF-001"))
        val second = notificationDeliveryService.consumeEvent(sampleEvent("OBX-NOTIF-001"))

        assertEquals(false, first.replayed)
        assertEquals(true, second.replayed)
        assertEquals(1, first.items.size)
        assertEquals(first.items.single().deliveryRequestId, second.items.single().deliveryRequestId)
        assertEquals(NotificationDeliveryStatus.PENDING, first.items.single().status)
        assertEquals("SYNTHETIC_SMS_SINK", first.items.single().providerKind)
        assertEquals(true, first.items.single().syntheticOnly)
        assertTrue(first.items.single().maskedMessage.contains("LAB-***0001"))
        assertFalse(first.items.single().maskedMessage.contains("010-1234-5678"))
        assertEquals(1, countRows("notification_inbox_events"))
        assertEquals(1, countRows("notification_delivery_requests"))
        assertEquals(1, countRows("notification_delivery_attempts WHERE status = 'PENDING'"))
    }

    @Test
    fun `delivery failures retry then move to dead letter through durable state`() {
        val delivery = notificationDeliveryService.consumeEvent(sampleEvent("OBX-NOTIF-002")).items.single()

        val failed = notificationDeliveryService.recordFailure(
            delivery.deliveryRequestId,
            RecordNotificationFailureRequest(
                errorMessage = "Synthetic SMS sink unavailable",
                requestedBy = "notification-worker",
                reason = "Synthetic retry drill",
                deadLetterThreshold = 2
            )
        )
        val deadLetter = notificationDeliveryService.recordFailure(
            delivery.deliveryRequestId,
            RecordNotificationFailureRequest(
                errorMessage = "Synthetic SMS sink still unavailable",
                requestedBy = "notification-worker",
                reason = "Synthetic dead-letter drill",
                deadLetterThreshold = 2
            )
        )

        assertEquals(NotificationDeliveryStatus.FAILED, failed.status)
        assertEquals(NotificationDeliveryStatus.DEAD_LETTER, deadLetter.status)
        assertEquals(1, countRows("notification_dead_letters"))
        assertEquals(2, countRows("notification_delivery_attempts WHERE status IN ('FAILED', 'DEAD_LETTER')"))

        val rejected = assertThrows(NotificationDomainException::class.java) {
            notificationDeliveryService.markDelivered(
                delivery.deliveryRequestId,
                MarkNotificationDeliveredRequest(
                    requestedBy = "notification-worker",
                    reason = "Synthetic late provider ack"
                )
            )
        }
        assertEquals("NOTIFICATION_STATE_TRANSITION_REJECTED", rejected.code)
    }

    @Test
    fun `pending delivery can be marked delivered through synthetic provider sink`() {
        val delivery = notificationDeliveryService.consumeEvent(sampleEvent("OBX-NOTIF-003")).items.single()
        val delivered = notificationDeliveryService.markDelivered(
            delivery.deliveryRequestId,
            MarkNotificationDeliveredRequest(
                requestedBy = "notification-worker",
                reason = "Synthetic SMS sink accepted message"
            )
        )

        assertEquals(NotificationDeliveryStatus.DELIVERED, delivered.status)
        assertEquals(1, countRows("notification_delivery_attempts WHERE status = 'DELIVERED'"))
    }

    private fun sampleEvent(sourceEventId: String): ConsumeNotificationEventRequest =
        ConsumeNotificationEventRequest(
            sourceEventId = sourceEventId,
            eventType = "PaymentLedgerPostingRequested",
            recipientId = "CUS-NOTIF-001",
            channel = "SMS",
            payload = mapOf(
                "paymentInstructionId" to "PAY-NOTIF-001",
                "amountMinor" to 45_000,
                "currency" to "KRW",
                "accountNo" to "LAB-123-0001",
                "phone" to "010-1234-5678"
            ),
            requestedBy = "notification-event-consumer"
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
