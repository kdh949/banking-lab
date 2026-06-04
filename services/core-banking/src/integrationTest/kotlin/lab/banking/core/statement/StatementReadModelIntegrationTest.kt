package lab.banking.core.statement

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
@AutoConfigureMockMvc
@Testcontainers
class StatementReadModelIntegrationTest {
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
              audit_events,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              account_holds,
              account_limits,
              accounts,
              customer_kyc_profiles,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        seedSyntheticStatementFixtures()
    }

    @Test
    fun `statement confirmation certificate and access history are read-only ledger projections`() {
        mockMvc.perform(
            get("/api/customers/SYN-CUS-STMT-001/statements")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-001"))
                .queryParam("from", "2026-02-03")
                .queryParam("to", "2026-02-04")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-STMT-001"))
            .andExpect(jsonPath("$.openingBalanceMinor").value(100000))
            .andExpect(jsonPath("$.closingBalanceMinor").value(99000))
            .andExpect(jsonPath("$.debitTotalMinor").value(3500))
            .andExpect(jsonPath("$.creditTotalMinor").value(2500))
            .andExpect(jsonPath("$.netAmountMinor").value(-1000))
            .andExpect(jsonPath("$.lineCount").value(3))

        val confirmation = mockMvc.perform(
            get("/api/transactions/TX-STMT-TRANSFER/confirmation")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactionId").value("TX-STMT-TRANSFER"))
            .andExpect(jsonPath("$.balanced").value(true))
            .andExpect(jsonPath("$.totalDebitMinor").value(2500))
            .andExpect(jsonPath("$.totalCreditMinor").value(2500))
            .andExpect(jsonPath("$.postings.length()").value(2))
            .andReturn()

        val confirmationId = objectMapper.readTree(confirmation.response.contentAsString)
            .path("confirmationId")
            .asText()
        assertTrue(confirmationId.startsWith("TXCONF-"))

        mockMvc.perform(
            get("/api/transactions/TX-STMT-TRANSFER/confirmation")
                .header("Authorization", bearer("customer02", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-002"))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))

        mockMvc.perform(
            get("/api/transactions/TX-STMT-TRANSFER/confirmation")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        val firstCertificate = mockMvc.perform(
            get("/api/accounts/ACC-STMT-001/balance-certificate")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-001"))
                .queryParam("date", "2026-02-04")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountId").value("ACC-STMT-001"))
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-STMT-001"))
            .andExpect(jsonPath("$.balanceAsOfMinor").value(96500))
            .andExpect(jsonPath("$.currentLedgerBalanceMinor").value(96500))
            .andReturn()

        val firstCertificateId = objectMapper.readTree(firstCertificate.response.contentAsString)
            .path("certificateId")
            .asText()
        val secondCertificate = mockMvc.perform(
            get("/api/accounts/ACC-STMT-001/balance-certificate")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-001"))
                .queryParam("date", "2026-02-04")
        )
            .andExpect(status().isOk)
            .andReturn()
        assertEquals(firstCertificateId, objectMapper.readTree(secondCertificate.response.contentAsString).path("certificateId").asText())

        val accessHistory = mockMvc.perform(
            get("/api/customers/SYN-CUS-STMT-001/access-history")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-STMT-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customerId").value("SYN-CUS-STMT-001"))
            .andReturn()

        val accessHistoryJson = accessHistory.response.contentAsString
        assertTrue(accessHistoryJson.contains("STATEMENT_VIEW"))
        assertTrue(accessHistoryJson.contains("BALANCE_CERTIFICATE_VIEW"))
        assertFalse(accessHistoryJson.contains("AUD-STMT-OTHER"))

        assertEquals(0, unbalancedTransactionCount())
        assertEquals(96500, postingSum("ACC-STMT-001"))
        assertEquals(2500, postingSum("ACC-STMT-002"))
        assertEquals(0, countRows("ledger_transactions WHERE transaction_type LIKE 'STATEMENT%'"))
    }

    private fun seedSyntheticStatementFixtures() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('BANK', 'Synthetic Bank Suspense', 'INTERNAL', 'LOW'),
              ('SYN-CUS-STMT-001', 'Synthetic Statement Customer', 'STANDARD', 'LOW'),
              ('SYN-CUS-STMT-002', 'Synthetic Other Customer', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('BANK-SUSPENSE', 'BANK', 'LAB-000-000000', 'KRW', 'ACTIVE'),
              ('ACC-STMT-001', 'SYN-CUS-STMT-001', 'LAB-060-000001', 'KRW', 'ACTIVE'),
              ('ACC-STMT-002', 'SYN-CUS-STMT-001', 'LAB-060-000002', 'KRW', 'ACTIVE'),
              ('ACC-STMT-OTHER', 'SYN-CUS-STMT-002', 'LAB-060-000003', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_transactions (
              ledger_transaction_id, transaction_type, business_reference_id, idempotency_key,
              business_date, status, requested_by, requested_channel, posted_at, reason
            )
            VALUES
              ('TX-STMT-OPEN', 'SYNTHETIC_OPENING_BALANCE', 'TX-STMT-OPEN', 'SEED-TX-STMT-OPEN', DATE '2026-02-01', 'POSTED', 'SEED', 'SYNTHETIC_DATA_GENERATOR', TIMESTAMPTZ '2026-02-01T00:00:00Z', 'Synthetic opening balance'),
              ('TX-STMT-FEE', 'FEE_POSTING', 'TX-STMT-FEE', 'SEED-TX-STMT-FEE', DATE '2026-02-03', 'POSTED', 'ops01', 'OPS_CONSOLE', TIMESTAMPTZ '2026-02-03T00:00:00Z', 'Synthetic statement fee'),
              ('TX-STMT-TRANSFER', 'INTERNAL_TRANSFER', 'TX-STMT-TRANSFER', 'SEED-TX-STMT-TRANSFER', DATE '2026-02-04', 'POSTED', 'customer01', 'CUSTOMER_WEB', TIMESTAMPTZ '2026-02-04T00:00:00Z', 'Synthetic statement transfer')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO ledger_postings (
              ledger_posting_id, ledger_transaction_id, account_id, currency, direction, amount_minor, posting_type
            )
            VALUES
              ('LP-STMT-OPEN-D', 'TX-STMT-OPEN', 'BANK-SUSPENSE', 'KRW', 'DEBIT', 100000, 'OPENING'),
              ('LP-STMT-OPEN-C', 'TX-STMT-OPEN', 'ACC-STMT-001', 'KRW', 'CREDIT', 100000, 'OPENING'),
              ('LP-STMT-FEE-D', 'TX-STMT-FEE', 'ACC-STMT-001', 'KRW', 'DEBIT', 1000, 'FEE'),
              ('LP-STMT-FEE-C', 'TX-STMT-FEE', 'BANK-SUSPENSE', 'KRW', 'CREDIT', 1000, 'FEE'),
              ('LP-STMT-TRANSFER-D', 'TX-STMT-TRANSFER', 'ACC-STMT-001', 'KRW', 'DEBIT', 2500, 'PRINCIPAL'),
              ('LP-STMT-TRANSFER-C', 'TX-STMT-TRANSFER', 'ACC-STMT-002', 'KRW', 'CREDIT', 2500, 'PRINCIPAL')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('BANK-SUSPENSE', 'KRW', -99000, -99000, 0),
              ('ACC-STMT-001', 'KRW', 96500, 96500, 0),
              ('ACC-STMT-002', 'KRW', 2500, 2500, 0),
              ('ACC-STMT-OTHER', 'KRW', 0, 0, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO audit_events (
              audit_event_id, event_type, actor_type, actor_id, actor_role,
              customer_id, payload_hash, payload_json
            )
            VALUES (
              'AUD-STMT-OTHER', 'ACCOUNT_VIEW', 'CUSTOMER', 'customer02', 'CUSTOMER',
              'SYN-CUS-STMT-002', 'seed-other', '{"syntheticOnly":true}'::jsonb
            )
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun bearer(subject: String, roles: List<String>, customerId: String? = null): String {
        val payload = linkedMapOf<String, Any?>(
            "iss" to "http://keycloak.local/realms/banking-lab",
            "sub" to subject,
            "roles" to roles,
            "customerId" to customerId,
            "active" to true
        )
        val json = objectMapper.writeValueAsBytes(payload)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json)
        return "Bearer lab.$encoded.sig"
    }

    private fun countRows(tableExpression: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $tableExpression", emptyMap<String, Any?>(), Int::class.java) ?: 0

    private fun postingSum(accountId: String): Long =
        jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END), 0)
            FROM ledger_postings
            WHERE account_id = :accountId
            """.trimIndent(),
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

    private fun unbalancedTransactionCount(): Int =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM (
              SELECT ledger_transaction_id,
                     currency,
                     SUM(CASE WHEN direction = 'DEBIT' THEN -amount_minor ELSE amount_minor END) AS signed_total
              FROM ledger_postings
              GROUP BY ledger_transaction_id, currency
            ) totals
            WHERE signed_total <> 0
            """.trimIndent(),
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("banking_lab_statement_test")
            .withUsername("banking_lab")
            .withPassword("banking_lab")
            .withReuse(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.locations") {
                val userDir = Paths.get(System.getProperty("user.dir"))
                listOf(
                    "filesystem:${userDir.resolve("db/migrations").normalize()}",
                    "filesystem:${userDir.resolve("../../db/migrations").normalize()}"
                ).joinToString(",")
            }
        }
    }
}
