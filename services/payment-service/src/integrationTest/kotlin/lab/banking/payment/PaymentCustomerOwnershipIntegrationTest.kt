package lab.banking.payment

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
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
class PaymentCustomerOwnershipIntegrationTest {
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
              payment_cancellation_requests,
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
    fun `customer payment identity is derived from token and cross customer access is concealed`() {
        val forgedCreateBody = """
            {
              "customerId": "CUS-FORGED",
              "debitAccountId": "ACC-OWNERSHIP-001",
              "billerId": "SYN-BILLER-UTIL-001",
              "amountMinor": 25000,
              "currency": "KRW",
              "idempotencyKey": "PAY-OWNERSHIP-CREATE-001",
              "requestedBy": "forged-actor",
              "requestedChannel": "PAYMENT_SERVICE",
              "reason": "Synthetic ownership payment"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/instructions")
                .header("Authorization", bearer("customer-without-claim", listOf("CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCreateBody)
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_CUSTOMER_CLAIM_REQUIRED"))

        val created = mockMvc.perform(
            post("/api/payments/instructions")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCreateBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.customerId").value("CUS-OWNERSHIP-001"))
            .andReturn()
        val instructionId = objectMapper.readTree(created.response.contentAsString)
            .at("/item/paymentInstructionId")
            .asText()

        assertEquals(
            1,
            countRows("payment_instructions WHERE payment_instruction_id = '$instructionId' AND customer_id = 'CUS-OWNERSHIP-001'")
        )
        assertEquals(
            1,
            countRows("payment_status_history WHERE payment_instruction_id = '$instructionId' AND actor_id = 'customer01'")
        )
        assertEquals(
            1,
            countRows(
                "payment_outbox_events WHERE aggregate_id = '$instructionId' " +
                    "AND payload_json->>'customerId' = 'CUS-OWNERSHIP-001' " +
                    "AND payload_json->>'requestedBy' = 'customer01' " +
                    "AND payload_json->>'requestedChannel' = 'CUSTOMER_WEB'"
            )
        )

        mockMvc.perform(
            get("/api/payments/instructions/$instructionId")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-002"))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_INSTRUCTION_NOT_FOUND"))

        val forgedCancelBody = """
            {
              "idempotencyKey": "PAY-OWNERSHIP-CANCEL-001",
              "requestedBy": "forged-cancel-actor",
              "reason": "Synthetic owner cancellation"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/cancel")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-002"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCancelBody)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_INSTRUCTION_NOT_FOUND"))
        assertEquals(1, countRows("payment_instructions WHERE payment_instruction_id = '$instructionId' AND status = 'POSTING_REQUESTED'"))

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/cancel")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCancelBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("CANCELED"))
        assertEquals(
            1,
            countRows("payment_status_history WHERE payment_instruction_id = '$instructionId' AND status = 'CANCELED' AND actor_id = 'customer01'")
        )
    }

    @Test
    fun `autopay identity is token bound and another customer cannot read or mutate it`() {
        val nextRunOn = LocalDate.now().plusDays(2)
        val forgedCreateBody = """
            {
              "customerId": "CUS-FORGED",
              "debitAccountId": "ACC-AUTOPAY-OWNERSHIP-001",
              "billerId": "SYN-BILLER-TELCO-001",
              "amountMinor": 33000,
              "currency": "KRW",
              "frequency": "MONTHLY",
              "nextRunOn": "$nextRunOn",
              "idempotencyKey": "AUTOPAY-OWNERSHIP-CREATE-001",
              "requestedBy": "forged-autopay-actor",
              "requestedChannel": "OPS_CONSOLE",
              "reason": "Synthetic ownership autopay"
            }
        """.trimIndent()

        val created = mockMvc.perform(
            post("/api/payments/autopay/agreements")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedCreateBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.customerId").value("CUS-OWNERSHIP-001"))
            .andReturn()
        val agreementId = objectMapper.readTree(created.response.contentAsString)
            .at("/item/autopayAgreementId")
            .asText()

        assertEquals(
            1,
            countRows(
                "payment_autopay_agreements WHERE autopay_agreement_id = '$agreementId' " +
                    "AND customer_id = 'CUS-OWNERSHIP-001' AND created_by = 'customer01' AND customer_identity_bound = true"
            )
        )

        mockMvc.perform(
            get("/api/payments/autopay/agreements/$agreementId")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-002"))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_AUTOPAY_NOT_FOUND"))

        val forgedPauseBody = """
            {
              "idempotencyKey": "AUTOPAY-OWNERSHIP-PAUSE-001",
              "requestedBy": "forged-pause-actor",
              "reason": "Synthetic owner pause"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/payments/autopay/agreements/$agreementId/pause")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-002"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedPauseBody)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PAYMENT_AUTOPAY_NOT_FOUND"))

        mockMvc.perform(
            post("/api/payments/autopay/agreements/$agreementId/pause")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "CUS-OWNERSHIP-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgedPauseBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("PAUSED"))

        assertEquals(
            1,
            countRows(
                "payment_autopay_status_history WHERE autopay_agreement_id = '$agreementId' " +
                    "AND status = 'PAUSED' AND actor_id = 'customer01'"
            )
        )
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
