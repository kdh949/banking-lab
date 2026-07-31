package lab.banking.core.ledger.evidence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.Base64
import lab.banking.core.ledger.application.BillPaymentCommand
import lab.banking.core.ledger.application.DepositCommand
import lab.banking.core.ledger.application.LedgerCommandService
import lab.banking.core.ledger.domain.BANK_CARD_CLEARING_ACCOUNT_ID
import org.junit.jupiter.api.Assertions.assertEquals
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
class PaymentLedgerEvidenceIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    lateinit var ledgerCommandService: LedgerCommandService

    @Test
    fun `payment service reads balanced bill-payment evidence by business date with audited reason`() {
        val ledgerTransactionId = seedLedgerEvidence()

        mockMvc.perform(
            get("/api/ledger/payment-postings/evidence")
                .queryParam("businessDate", "2026-07-30")
                .queryParam("reason", "Three-way payment reconciliation")
        )
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/api/ledger/payment-postings/evidence")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER")))
                .queryParam("businessDate", "2026-07-30")
                .queryParam("reason", "Three-way payment reconciliation")
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/ledger/payment-postings/evidence")
                .header("Authorization", bearer("payment-service", listOf("PAYMENT_SERVICE")))
                .queryParam("businessDate", "2026-07-30")
        )
            .andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/ledger/payment-postings/evidence")
                .header("Authorization", bearer("payment-service", listOf("PAYMENT_SERVICE")))
                .queryParam("businessDate", "2026-07-30")
                .queryParam("reason", "Three-way payment reconciliation")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditEventId").value(org.hamcrest.Matchers.startsWith("AUD-")))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].ledgerTransactionId").value(ledgerTransactionId))
            .andExpect(jsonPath("$.items[0].paymentInstructionId").value("PAY-EVIDENCE-001"))
            .andExpect(jsonPath("$.items[0].transactionType").value("BILL_PAYMENT"))
            .andExpect(jsonPath("$.items[0].status").value("POSTED"))
            .andExpect(jsonPath("$.items[0].amountMinor").value(45000))
            .andExpect(jsonPath("$.items[0].totalDebitMinor").value(45000))
            .andExpect(jsonPath("$.items[0].totalCreditMinor").value(45000))
            .andExpect(jsonPath("$.items[0].postingCount").value(2))
            .andExpect(jsonPath("$.items[0].balanced").value(true))

        val auditCount = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM audit_events
            WHERE event_type = 'PAYMENT_LEDGER_EVIDENCE_VIEW'
              AND reason = 'Three-way payment reconciliation'
            """.trimIndent(),
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0
        assertEquals(1, auditCount)
    }

    private fun seedLedgerEvidence(): String {
        val businessDate = LocalDate.of(2026, 7, 30)

        // Exercise the production posting path rather than reproducing ledger rows
        // with raw SQL. The migration-owned synthetic clearing account is funded
        // first, then debited by the real bill-payment command.
        ledgerCommandService.deposit(
            DepositCommand(
                accountId = BANK_CARD_CLEARING_ACCOUNT_ID,
                amountMinor = 45_000,
                idempotencyKey = "IDEMP-PAY-EVIDENCE-SEED",
                requestedBy = "payment-evidence-test",
                requestedChannel = "CORE_BANKING",
                businessDate = businessDate,
                reason = "Synthetic payment ledger evidence seed"
            )
        )

        return ledgerCommandService.billPayment(
            BillPaymentCommand(
                paymentInstructionId = "PAY-EVIDENCE-001",
                debitAccountId = BANK_CARD_CLEARING_ACCOUNT_ID,
                syntheticBillerId = "BILLER-EVIDENCE-001",
                amountMinor = 45_000,
                idempotencyKey = "IDEMP-PAY-EVIDENCE-001",
                requestedBy = "payment-service",
                requestedChannel = "PAYMENT_SERVICE",
                businessDate = businessDate,
                reason = "Synthetic reconciliation evidence fixture"
            )
        ).value.id
    }

    private fun bearer(subject: String, roles: List<String>): String {
        val payload = mapOf<String, Any>(
            "sub" to subject,
            "roles" to roles,
            "active" to true
        )
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(payload))
        return "Bearer lab.$encoded.sig"
    }

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
