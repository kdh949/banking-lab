package lab.banking.payment

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
class PaymentAuthorizationIntegrationTest {
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
              payment_access_audit_events,
              payment_autopay_executions,
              payment_autopay_status_history,
              payment_autopay_agreements,
              payment_outbox_events,
              payment_status_history,
              payment_attempts,
              payment_idempotency_keys,
              payment_instructions
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
    }

    @Test
    fun `payment instruction routes enforce customer staff and payment service roles`() {
        val createBody = """
            {
              "customerId": "CUS-PAY-AUTH-001",
              "debitAccountId": "ACC-PAY-AUTH-001",
              "billerId": "SYN-BILLER-UTIL-001",
              "amountMinor": 45000,
              "currency": "KRW",
              "idempotencyKey": "PAY-AUTH-CREATE-001",
              "requestedBy": "customer01",
              "requestedChannel": "CUSTOMER_WEB",
              "reason": "Synthetic authorization payment"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/instructions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            post("/api/payments/instructions")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.policy").value("PAYMENT_RBAC_ROUTE_POLICY"))

        assertEquals(0, countRows("payment_instructions"))

        val created = mockMvc.perform(
            post("/api/payments/instructions")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("POSTING_REQUESTED"))
            .andReturn()
        val instructionId = objectMapper.readTree(created.response.contentAsString)
            .at("/item/paymentInstructionId")
            .asText()

        mockMvc.perform(
            get("/api/payments/instructions/$instructionId")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_LOOKUP_REASON_REQUIRED"))

        mockMvc.perform(
            get("/api/payments/instructions/$instructionId")
                .queryParam("reason", "Synthetic staff payment inquiry")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.paymentInstructionId").value(instructionId))
            .andExpect(jsonPath("$.auditEventId").value(org.hamcrest.Matchers.startsWith("PAU-")))
        assertEquals(1, countRows("payment_access_audit_events WHERE event_type = 'PAYMENT_INSTRUCTION_VIEW' AND reason = 'Synthetic staff payment inquiry'"))

        val cancelCreateBody = """
            {
              "customerId": "CUS-PAY-AUTH-001",
              "debitAccountId": "ACC-PAY-AUTH-001",
              "billerId": "SYN-BILLER-UTIL-001",
              "amountMinor": 47000,
              "currency": "KRW",
              "idempotencyKey": "PAY-AUTH-CANCEL-CREATE-001",
              "requestedBy": "customer01",
              "requestedChannel": "CUSTOMER_WEB",
              "reason": "Synthetic authorization payment cancel target"
            }
        """.trimIndent()
        val cancelCreated = mockMvc.perform(
            post("/api/payments/instructions")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelCreateBody)
        )
            .andExpect(status().isCreated)
            .andReturn()
        val cancelInstructionId = objectMapper.readTree(cancelCreated.response.contentAsString)
            .at("/item/paymentInstructionId")
            .asText()
        val cancelBody = """
            {
              "idempotencyKey": "PAY-AUTH-CANCEL-001",
              "requestedBy": "customer01",
              "reason": "Synthetic customer cancels own payment"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/instructions/$cancelInstructionId/cancel")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            post("/api/payments/instructions/$cancelInstructionId/cancel")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CANCELED"))
        assertEquals(1, countRows("payment_instructions WHERE status = 'CANCELED'"))

        val settlementBody = """
            {
              "ledgerTransactionId": "TX-PAY-AUTH-001",
              "idempotencyKey": "PAY-AUTH-SETTLE-001",
              "requestedBy": "payment-service",
              "reason": "Synthetic route-authorized settlement"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/settlements")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementBody)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/settlements")
                .header("Authorization", bearer("payment-service", listOf("PAYMENT_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("SETTLED"))

        assertEquals(1, countRows("payment_instructions WHERE status = 'SETTLED'"))
    }

    @Test
    fun `autopay and outbox operation routes enforce customer and service roles`() {
        val autopayBody = """
            {
              "customerId": "CUS-PAY-AUTH-001",
              "debitAccountId": "ACC-PAY-AUTH-001",
              "billerId": "SYN-BILLER-UTIL-001",
              "amountMinor": 45000,
              "currency": "KRW",
              "frequency": "MONTHLY",
              "nextRunOn": "2026-01-31",
              "idempotencyKey": "PAY-AUTH-APAY-001",
              "requestedBy": "customer01",
              "requestedChannel": "CUSTOMER_WEB",
              "reason": "Synthetic authorization autopay"
            }
        """.trimIndent()
        val created = mockMvc.perform(
            post("/api/payments/autopay/agreements")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(autopayBody)
        )
            .andExpect(status().isCreated)
            .andReturn()
        val agreementId = objectMapper.readTree(created.response.contentAsString)
            .at("/item/autopayAgreementId")
            .asText()
        val pauseBody = """
            {
              "idempotencyKey": "PAY-AUTH-APAY-PAUSE-001",
              "requestedBy": "customer01",
              "reason": "Synthetic customer pauses autopay"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/autopay/agreements/$agreementId/pause")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pauseBody)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/autopay/agreements/$agreementId/pause")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pauseBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("PAUSED"))

        val dueBody = """
            {
              "businessDate": "2026-01-31",
              "idempotencyKey": "PAY-AUTH-APAY-DUE-001",
              "requestedBy": "payment-service",
              "reason": "Synthetic authorization due run",
              "limit": 10
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/autopay/executions/due")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dueBody)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/autopay/executions/due")
                .header("Authorization", bearer("payment-service", listOf("PAYMENT_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dueBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executedCount").value(0))

        val dispatchBody = """
            {
              "requestedBy": "payment-service",
              "reason": "Synthetic authorization outbox dispatch",
              "deadLetterThreshold": 3
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/outbox/ledger-postings/dispatch-next")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-PAY-AUTH-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatchBody)
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/payments/outbox/ledger-postings/dispatch-next")
                .header("Authorization", bearer("payment-service", listOf("PAYMENT_SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dispatchBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("NO_PENDING_EVENT"))
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
