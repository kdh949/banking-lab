package lab.banking.notification

import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.NotificationDomainException
import lab.banking.notification.domain.NotificationPreferenceService
import lab.banking.notification.domain.UpsertNotificationPreferenceRequest
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
class NotificationPreferenceIntegrationTest {
    @Autowired
    lateinit var notificationPreferenceService: NotificationPreferenceService

    @Autowired
    lateinit var notificationDeliveryService: NotificationDeliveryService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              notification_access_audit_events,
              notification_suppressed_events,
              notification_recipient_preferences,
              notification_dead_letters,
              notification_delivery_attempts,
              notification_delivery_requests,
              notification_inbox_events
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `disabled wildcard preference suppresses delivery and records masked audit`() {
        val preference = notificationPreferenceService.upsertPreference(
            UpsertNotificationPreferenceRequest(
                recipientId = "CUS-NOTIF-PREF-001",
                channel = "SMS",
                enabled = false,
                requestedBy = "ops-preference-admin",
                reason = "Synthetic customer opted out of SMS notices"
            )
        )

        assertEquals("*", preference.eventType)
        assertEquals(false, preference.enabled)

        val first = notificationDeliveryService.consumeEvent(sampleEvent("OBX-PREF-001", "CUS-NOTIF-PREF-001"))
        val replay = notificationDeliveryService.consumeEvent(sampleEvent("OBX-PREF-001", "CUS-NOTIF-PREF-001"))

        assertEquals(false, first.replayed)
        assertEquals(true, replay.replayed)
        assertEquals(0, first.items.size)
        assertEquals(0, replay.items.size)
        assertEquals(1, countRows("notification_inbox_events"))
        assertEquals(0, countRows("notification_delivery_requests"))
        assertEquals(0, countRows("notification_delivery_attempts"))
        assertEquals(1, countRows("notification_suppressed_events"))

        val maskedPayload = singleString(
            "SELECT masked_payload_json::text FROM notification_suppressed_events WHERE source_event_id = :sourceEventId",
            mapOf("sourceEventId" to "OBX-PREF-001")
        )
        assertTrue(maskedPayload.contains("LAB-***0001"), maskedPayload)
        assertFalse(maskedPayload.contains("LAB-PREF-0001"), maskedPayload)
        assertFalse(maskedPayload.contains("010-2222-0001"), maskedPayload)
    }

    @Test
    fun `event specific enabled preference overrides wildcard opt out`() {
        notificationPreferenceService.upsertPreference(
            UpsertNotificationPreferenceRequest(
                recipientId = "CUS-NOTIF-PREF-002",
                channel = "SMS",
                enabled = false,
                requestedBy = "ops-preference-admin",
                reason = "Synthetic wildcard opt out"
            )
        )
        notificationPreferenceService.upsertPreference(
            UpsertNotificationPreferenceRequest(
                recipientId = "CUS-NOTIF-PREF-002",
                channel = "SMS",
                eventType = "PaymentLedgerPostingRequested",
                enabled = true,
                requestedBy = "ops-preference-admin",
                reason = "Synthetic payment notices remain enabled"
            )
        )

        val delivery = notificationDeliveryService
            .consumeEvent(sampleEvent("OBX-PREF-002", "CUS-NOTIF-PREF-002"))
            .items
            .single()

        assertEquals("CUS-NOTIF-PREF-002", delivery.recipientId)
        assertEquals("SYNTHETIC_SMS_SINK", delivery.providerKind)
        assertEquals(
            2,
            notificationPreferenceService.preferences(
                recipientId = "CUS-NOTIF-PREF-002",
                channel = "SMS",
                requestedBy = "audit-preference-reviewer",
                reason = "Synthetic preference lookup"
            ).size
        )
        assertEquals(1, countRows("notification_access_audit_events WHERE action = 'NOTIFICATION_PREFERENCE_VIEW'"))
        assertEquals(0, countRows("notification_suppressed_events"))
        assertEquals(1, countRows("notification_delivery_requests"))
    }

    @Test
    fun `preference administration requires reason and synthetic boundary`() {
        val missingReason = assertThrows(NotificationDomainException::class.java) {
            notificationPreferenceService.upsertPreference(
                UpsertNotificationPreferenceRequest(
                    recipientId = "CUS-NOTIF-PREF-003",
                    channel = "PUSH",
                    enabled = true,
                    requestedBy = "ops-preference-admin",
                    reason = " "
                )
            )
        }
        assertEquals("POLICY_REASON_REQUIRED", missingReason.code)

        val realProviderBoundary = assertThrows(NotificationDomainException::class.java) {
            notificationPreferenceService.upsertPreference(
                UpsertNotificationPreferenceRequest(
                    recipientId = "CUS-NOTIF-PREF-003",
                    channel = "PUSH",
                    enabled = true,
                    requestedBy = "ops-preference-admin",
                    reason = "Synthetic boundary test",
                    syntheticOnly = false
                )
            )
        }
        assertEquals("NOTIFICATION_REAL_PROVIDER_FORBIDDEN", realProviderBoundary.code)
    }

    private fun sampleEvent(sourceEventId: String, recipientId: String): ConsumeNotificationEventRequest =
        ConsumeNotificationEventRequest(
            sourceEventId = sourceEventId,
            eventType = "PaymentLedgerPostingRequested",
            recipientId = recipientId,
            channel = "SMS",
            payload = mapOf(
                "paymentInstructionId" to "PAY-NOTIF-PREF-001",
                "amountMinor" to 45_000,
                "currency" to "KRW",
                "accountNo" to "LAB-PREF-0001",
                "phone" to "010-2222-0001"
            ),
            requestedBy = "notification-event-consumer"
        )

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun singleString(sql: String, params: Map<String, Any?>): String =
        jdbc.queryForObject(sql, params, String::class.java) ?: ""

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
