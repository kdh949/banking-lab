package lab.banking.notification

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "banking-lab.security.enabled=true",
        "banking-lab.security.simulator-tokens-enabled=true",
        "banking-lab.security.dev-simulator-token-enabled=true"
    ]
)
@AutoConfigureMockMvc
@Testcontainers
class NotificationAuthorizationIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

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
    fun `notification routes enforce service operator and auditor roles`() {
        val eventBody = """
            {
              "sourceEventId": "OBX-NOTIF-AUTH-001",
              "eventType": "PaymentLedgerPostingRequested",
              "recipientId": "CUS-NOTIF-AUTH-001",
              "channel": "SMS",
              "payload": {
                "paymentInstructionId": "PAY-NOTIF-AUTH-001",
                "amountMinor": 45000,
                "currency": "KRW",
                "accountNo": "LAB-123-0001",
                "phone": "010-1234-5678"
              },
              "requestedBy": "notification-event-consumer"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/notifications/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("NOTIFICATION_AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            post("/api/notifications/events")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-NOTIF-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody)
        )
            .andExpect(status().isForbidden)

        val consumed = mockMvc.perform(
            post("/api/notifications/events")
                .header("Authorization", bearer("notification-service", listOf("NOTIFICATION_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andReturn()
        val deliveryRequestId = objectMapper.readTree(consumed.response.contentAsString)
            .at("/items/0/deliveryRequestId")
            .asText()
        assertEquals(1, countRows("notification_delivery_requests"))

        mockMvc.perform(
            get("/api/notifications/deliveries/$deliveryRequestId")
                .header("Authorization", bearer("audit01", listOf("AUDITOR")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.maskedMessage").value(org.hamcrest.Matchers.containsString("LAB-***0001")))

        val deliveredBody = """
            {
              "requestedBy": "notification-worker",
              "reason": "Synthetic sink accepted delivery"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/notifications/deliveries/$deliveryRequestId/delivered")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-NOTIF-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deliveredBody)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/notifications/deliveries/$deliveryRequestId/delivered")
                .header("Authorization", bearer("notification-service", listOf("NOTIFICATION_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deliveredBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DELIVERED"))

        assertEquals(1, countRows("notification_delivery_attempts WHERE status = 'DELIVERED'"))
    }

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = mutableMapOf<String, Any>(
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        if (customerId != null) {
            payload["customerId"] = customerId
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

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
