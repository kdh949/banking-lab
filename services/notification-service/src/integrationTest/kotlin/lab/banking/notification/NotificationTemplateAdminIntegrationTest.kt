package lab.banking.notification

import lab.banking.notification.domain.ApproveNotificationTemplateChangeRequest
import lab.banking.notification.domain.ConsumeNotificationEventRequest
import lab.banking.notification.domain.CreateNotificationTemplateChangeRequest
import lab.banking.notification.domain.NotificationDeliveryService
import lab.banking.notification.domain.NotificationDomainException
import lab.banking.notification.domain.NotificationTemplateAdminService
import lab.banking.notification.domain.NotificationTemplateChangeStatus
import lab.banking.notification.domain.RejectNotificationTemplateChangeRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
class NotificationTemplateAdminIntegrationTest {
    @Autowired
    lateinit var templateAdminService: NotificationTemplateAdminService

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
              notification_inbox_events,
              notification_workflow_events,
              notification_workflow_instances,
              notification_template_change_requests
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            DELETE FROM notification_templates
            WHERE event_type LIKE 'TemplateAdmin%'
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    @Test
    fun `template change approval activates chat template and enforces maker checker separation`() {
        val pending = templateAdminService.createChangeRequest(
            CreateNotificationTemplateChangeRequest(
                eventType = "TemplateAdminApproved",
                channel = "CHAT",
                version = 1,
                bodyTemplate = "Synthetic chat notice {paymentInstructionId} for {accountNo} and {phone}.",
                providerKind = "SYNTHETIC_CHAT_SINK",
                requestedBy = "ops-maker01",
                reason = "Synthetic chat notification template onboarding"
            )
        )

        assertEquals(NotificationTemplateChangeStatus.PENDING, pending.status)
        assertEquals("PENDING_REVIEW", pending.workflowStatus.name)
        assertEquals(1, pending.workflowTimeline.size)
        assertEquals("REQUESTED", pending.workflowTimeline.single().eventType)
        assertEquals(pending.workflowInstanceId, templateAdminService.changeRequests("PENDING").single().workflowInstanceId)
        assertEquals(0, templateAdminService.templates("TemplateAdminApproved", "CHAT").size)
        val notFound = assertThrows(NotificationDomainException::class.java) {
            notificationDeliveryService.consumeEvent(sampleEvent("OBX-TEMPLATE-001", "TemplateAdminApproved", "CHAT"))
        }
        assertEquals("NOTIFICATION_TEMPLATE_NOT_FOUND", notFound.code)

        val selfApproval = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.approveChangeRequest(
                pending.changeRequestId,
                ApproveNotificationTemplateChangeRequest(
                    approvedBy = "ops-maker01",
                    approvedByRole = "OPS_MANAGER",
                    reason = "Synthetic self approval attempt"
                )
            )
        }
        assertEquals("MAKER_CHECKER_SELF_APPROVAL_REJECTED", selfApproval.code)
        assertEquals("MAKER_CHECKER_SEPARATION_OF_DUTIES", selfApproval.policy)

        val approved = templateAdminService.approveChangeRequest(
            pending.changeRequestId,
            ApproveNotificationTemplateChangeRequest(
                approvedBy = "ops-checker01",
                approvedByRole = "OPS_MANAGER",
                reason = "Synthetic checker approval for chat template"
            )
        )

        assertEquals(NotificationTemplateChangeStatus.APPROVED, approved.status)
        assertEquals(pending.workflowInstanceId, approved.workflowInstanceId)
        assertEquals("APPROVED", approved.workflowStatus.name)
        assertEquals(listOf("REQUESTED", "APPROVED"), approved.workflowTimeline.map { it.eventType })
        assertEquals(listOf("PENDING_REVIEW", "APPROVED"), approved.workflowTimeline.map { it.toStatus.name })
        assertEquals("ops-checker01", approved.reviewedBy)
        assertNotNull(approved.approvedTemplateId)
        assertEquals(1, templateAdminService.templates("TemplateAdminApproved", "CHAT").size)

        val delivery = notificationDeliveryService
            .consumeEvent(sampleEvent("OBX-TEMPLATE-002", "TemplateAdminApproved", "CHAT"))
            .items
            .single()

        assertEquals("CHAT", delivery.channel)
        assertEquals("SYNTHETIC_CHAT_SINK", delivery.providerKind)
        assertTrue(delivery.maskedMessage.contains("ACC-***7777"), delivery.maskedMessage)
        assertFalse(delivery.maskedMessage.contains("ACC-TEMPLATE-7777"), delivery.maskedMessage)
        assertFalse(delivery.maskedMessage.contains("010-1111-7777"), delivery.maskedMessage)
        assertEquals(1, countRows("notification_template_change_requests WHERE status = 'APPROVED'"))
        assertEquals(1, countRows("notification_workflow_instances WHERE status = 'APPROVED' AND business_reference_id = '${pending.changeRequestId}'"))
        assertEquals(2, countRows("notification_workflow_events WHERE workflow_instance_id = '${pending.workflowInstanceId}'"))
    }

    @Test
    fun `template rejection is terminal and does not activate template`() {
        val pending = templateAdminService.createChangeRequest(
            CreateNotificationTemplateChangeRequest(
                eventType = "TemplateAdminRejected",
                channel = "PUSH",
                version = 1,
                bodyTemplate = "Synthetic push notice {caseId}.",
                providerKind = "SYNTHETIC_PUSH_SINK",
                requestedBy = "ops-maker02",
                reason = "Synthetic push template review"
            )
        )
        val rejected = templateAdminService.rejectChangeRequest(
            pending.changeRequestId,
            RejectNotificationTemplateChangeRequest(
                rejectedBy = "compliance01",
                rejectedByRole = "COMPLIANCE_MANAGER",
                reason = "Synthetic template wording rejected"
            )
        )

        assertEquals(NotificationTemplateChangeStatus.REJECTED, rejected.status)
        assertEquals(NotificationTemplateChangeStatus.REJECTED.name, templateAdminService.changeRequests("REJECTED").single().status.name)
        assertEquals("REJECTED", rejected.workflowStatus.name)
        assertEquals(listOf("REQUESTED", "REJECTED"), rejected.workflowTimeline.map { it.eventType })
        assertEquals(0, templateAdminService.templates("TemplateAdminRejected", "PUSH").size)
        val secondDecision = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.approveChangeRequest(
                pending.changeRequestId,
                ApproveNotificationTemplateChangeRequest(
                    approvedBy = "ops-checker02",
                    approvedByRole = "OPS_MANAGER",
                    reason = "Synthetic late approval"
                )
            )
        }
        assertEquals("NOTIFICATION_TEMPLATE_CHANGE_NOT_PENDING", secondDecision.code)
    }

    @Test
    fun `template administration requires reason synthetic provider and checker role`() {
        val missingReason = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.createChangeRequest(
                CreateNotificationTemplateChangeRequest(
                    eventType = "TemplateAdminMissingReason",
                    channel = "SMS",
                    version = 1,
                    bodyTemplate = "Synthetic SMS notice {accountNo}.",
                    providerKind = "SYNTHETIC_SMS_SINK",
                    requestedBy = "ops-maker03",
                    reason = " "
                )
            )
        }
        assertEquals("POLICY_REASON_REQUIRED", missingReason.code)

        val realProvider = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.createChangeRequest(
                CreateNotificationTemplateChangeRequest(
                    eventType = "TemplateAdminRealProvider",
                    channel = "SMS",
                    version = 1,
                    bodyTemplate = "Synthetic SMS notice {accountNo}.",
                    providerKind = "REAL_SMS_PROVIDER",
                    requestedBy = "ops-maker03",
                    reason = "Synthetic provider boundary test"
                )
            )
        }
        assertEquals("NOTIFICATION_REAL_PROVIDER_FORBIDDEN", realProvider.code)

        val rawSensitiveLiteral = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.createChangeRequest(
                CreateNotificationTemplateChangeRequest(
                    eventType = "TemplateAdminRawPii",
                    channel = "EMAIL",
                    version = 1,
                    bodyTemplate = "Synthetic email notice for ACC-9999-0001 and 010-9999-0001.",
                    providerKind = "SYNTHETIC_EMAIL_SINK",
                    requestedBy = "ops-maker03",
                    reason = "Synthetic masking boundary test"
                )
            )
        }
        assertEquals("NOTIFICATION_TEMPLATE_RAW_PII_FORBIDDEN", rawSensitiveLiteral.code)

        val pending = templateAdminService.createChangeRequest(
            CreateNotificationTemplateChangeRequest(
                eventType = "TemplateAdminRole",
                channel = "SMS",
                version = 1,
                bodyTemplate = "Synthetic SMS notice {accountNo}.",
                providerKind = "SYNTHETIC_SMS_SINK",
                requestedBy = "ops-maker03",
                reason = "Synthetic checker role test"
            )
        )
        val wrongRole = assertThrows(NotificationDomainException::class.java) {
            templateAdminService.approveChangeRequest(
                pending.changeRequestId,
                ApproveNotificationTemplateChangeRequest(
                    approvedBy = "ops-checker03",
                    approvedByRole = "OPS_OPERATOR",
                    reason = "Synthetic checker role test"
                )
            )
        }
        assertEquals("NOTIFICATION_TEMPLATE_CHECKER_ROLE_REQUIRED", wrongRole.code)
    }

    private fun sampleEvent(sourceEventId: String, eventType: String, channel: String): ConsumeNotificationEventRequest =
        ConsumeNotificationEventRequest(
            sourceEventId = sourceEventId,
            eventType = eventType,
            recipientId = "CUS-TEMPLATE-001",
            channel = channel,
            payload = mapOf(
                "paymentInstructionId" to "PAY-TEMPLATE-001",
                "accountNo" to "ACC-TEMPLATE-7777",
                "phone" to "010-1111-7777",
                "syntheticOnly" to true
            ),
            requestedBy = "notification-template-test"
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
