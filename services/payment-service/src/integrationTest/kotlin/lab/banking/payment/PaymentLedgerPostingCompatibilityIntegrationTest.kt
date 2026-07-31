package lab.banking.payment

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import lab.banking.payment.domain.CreatePaymentInstructionRequest
import lab.banking.payment.domain.PaymentInstructionService
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
class PaymentLedgerPostingCompatibilityIntegrationTest {
    @Autowired
    lateinit var paymentInstructionService: PaymentInstructionService

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
    fun `deprecated settlement callback and canonical ledger posting callback share one idempotent result`() {
        val created = paymentInstructionService.createInstruction(
            CreatePaymentInstructionRequest(
                customerId = "CUS-LEDGER-COMPAT-001",
                debitAccountId = "ACC-LEDGER-COMPAT-001",
                billerId = "SYN-BILLER-UTIL-001",
                amountMinor = 27_000,
                currency = "KRW",
                idempotencyKey = "PAY-LEDGER-COMPAT-CREATE-001",
                requestedBy = "customer-compat01",
                requestedChannel = "CUSTOMER_WEB",
                reason = "Synthetic ledger posting compatibility target"
            )
        )
        val instructionId = created.item.paymentInstructionId
        val callbackBody = objectMapper.writeValueAsString(
            mapOf(
                "ledgerTransactionId" to "TX-LEDGER-COMPAT-001",
                "idempotencyKey" to "PAY-LEDGER-COMPAT-CALLBACK-001",
                "requestedBy" to "payment-service",
                "reason" to "Synthetic compatibility ledger callback"
            )
        )
        val serviceToken = bearer("payment-service", listOf("PAYMENT_SERVICE"))

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/settlements")
                .header("Authorization", serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(callbackBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("LEDGER_POSTED"))
            .andExpect(jsonPath("$.item.ledgerTransactionId").value("TX-LEDGER-COMPAT-001"))
            .andExpect(jsonPath("$.replayed").value(false))

        mockMvc.perform(
            post("/api/payments/instructions/$instructionId/ledger-postings")
                .header("Authorization", serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(callbackBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.status").value("LEDGER_POSTED"))
            .andExpect(jsonPath("$.item.ledgerTransactionId").value("TX-LEDGER-COMPAT-001"))
            .andExpect(jsonPath("$.replayed").value(true))

        assertEquals(
            1,
            countRows("payment_instructions WHERE payment_instruction_id = '$instructionId' AND status = 'LEDGER_POSTED'")
        )
        assertEquals(
            1,
            countRows("payment_outbox_events WHERE aggregate_id = '$instructionId' AND event_type = 'PaymentInstructionLedgerPosted'")
        )
        assertEquals(
            0,
            countRows("payment_outbox_events WHERE aggregate_id = '$instructionId' AND event_type = 'PaymentInstructionSettled'")
        )
    }

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = mapOf<String, Any>(
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
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
