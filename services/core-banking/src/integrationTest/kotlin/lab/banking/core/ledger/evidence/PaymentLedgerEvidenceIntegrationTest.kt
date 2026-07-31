
package lab.banking.core.ledger.evidence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.Base64
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `payment service reads balanced bill-payment evidence by business date with audited reason`() {
        seedLedgerEvidence()

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
            .andExpect(jsonPath("$.items[0].ledgerTransactionId").value("TX-PAY-EVIDENCE-001"))
            .andExpect(jsonPath("$.items[0].paymentInstructionId").value("PAY-EVIDENCE-001"))
            .andExpect(jsonPath("$.items[0].transactionType").value("BILL_PAYMENT"))
            .andExpect(jsonPath("$.items[0].status").value("POSTED"))
            .andExpect(jsonPath("$.items[0].amountMinor").value(45000))
            .andExpect(jsonPath("$.items[0].totalDebitMinor").value(45000))
            .andExpect(jsonPath("$.items[0].totalCreditMinor").value(45000))
            .andExpect(jsonPath("$.items[0].postingCount").value(2))
            .andExpect(jsonPath("$.items[0].balanced").value(true))

        assertEquals(
            1,
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE event_type = 'PAYMENT_LEDGER_EVIDENCE_VIEW'
                  AND reason = 'Three-way payment reconciliation'
                """.trimIndent(),
                emptyMap<String, Any?>(),
                Int::class.java
            )
        )
    }

    private fun seedLedgerEvidence() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES ('CUS-PAY-EVIDENCE', 'Synthetic Evidence Customer', 'STANDARD', 'LOW')
            ON CONFLICT (customer_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (
              account_id, customer_id, account_no, currency, status,
              account_class, system_account_kind, synthetic_system_account
            ) VALUES (
              'ACC-PAY-EVIDENCE', 'CUS-PAY-EVIDENCE', 'LAB-EVIDENCE-001', 'KRW', 'ACTIVE',
              'LIABILITY', NULL, false
            )
            ON CONFLICT (account_id) DO NOTHING
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        TransactionTemplate(transactionManager).execute {
            jdbc.update(
                """
                INSERT INTO ledger_transactions (
                  ledger_transaction_id, transaction_type, business_reference_id,
                  idempotency_key, business_date, status, requested_by,
                  requested_channel, posted_at, reason
                ) VALUES (
                  'TX-PAY-EVIDENCE-001', 'BILL_PAYMENT', 'PAY-EVIDENCE-001',
                  'IDEMP-PAY-EVIDENCE-001', :businessDate, 'POSTED', 'payment-service',
                  'PAYMENT_SERVICE', now(), 'Synthetic reconciliation evidence fixture'
                )
                ON CONFLICT (ledger_transaction_id) DO NOTHING
                """.trimIndent(),
                mapOf("businessDate" to LocalDate.of(2026, 7, 30))
            )
            jdbc.update(
                """
                INSERT INTO ledger_postings (
                  ledger_posting_id, ledger_transaction_id, account_id,
                  currency, direction, amount_minor, posting_type
                ) VALUES
                  ('LP-PAY-EVIDENCE-001-D', 'TX-PAY-EVIDENCE-001', 'ACC-PAY-EVIDENCE', 'KRW', 'DEBIT', 45000, 'PAYMENT'),
                  ('LP-PAY-EVIDENCE-001-C', 'TX-PAY-EVIDENCE-001', 'BANK-SETTLEMENT', 'KRW', 'CREDIT', 45000, 'PAYMENT')
                ON CONFLICT (ledger_posting_id) DO NOTHING
                """.trimIndent(),
                emptyMap<String, Any?>()
            )
        }
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
