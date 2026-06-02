package lab.banking.core.ledger.api

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import lab.banking.core.ledger.application.LedgerCommandService
import org.junit.jupiter.api.AfterEach
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LedgerRuntimeApiParityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var ledgerCommandService: LedgerCommandService

    @Autowired
    lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun resetDatabase() {
        jdbc.jdbcTemplate.execute(
            """
            TRUNCATE TABLE
              aml_cases,
              fds_cases,
              reconciliation_items,
              inbox_events,
              outbox_events,
              workflow_events,
              workflow_instances,
              masking_access_logs,
              screen_access_logs,
              operator_approvals,
              audit_events,
              daily_closings,
              idempotency_keys,
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
    }

    @AfterEach
    fun verifyNoUnbalancedTransactions() {
        val unbalanced = jdbc.queryForObject(
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
        assertEquals(0, unbalanced)
    }

    @Test
    fun `ledger reversal endpoint preserves invariants and returns structured duplicate reversal policy error`() {
        seedAccount("CUS-A", "ACC-A", "LAB-API-000001")
        seedAccount("CUS-B", "ACC-B", "LAB-API-000002")

        mockMvc.perform(
            post("/api/ledger/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountId": "ACC-A",
                      "amountMinor": 100000,
                      "idempotencyKey": "API-DEP-001",
                      "requestedBy": "branch01",
                      "requestedChannel": "STAFF_TERMINAL"
                    }
                    """.trimIndent()
                )
        ).andExpect(status().isCreated)

        val transferResponse = mockMvc.perform(
            post("/api/ledger/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "fromAccountId": "ACC-A",
                      "toAccountId": "ACC-B",
                      "amountMinor": 12000,
                      "idempotencyKey": "API-TRF-001",
                      "requestedBy": "customer01",
                      "requestedChannel": "CUSTOMER_WEB"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val transferId = objectMapper.readTree(transferResponse.response.contentAsString)
            .path("value")
            .path("id")
            .asText()

        mockMvc.perform(
            post("/api/ledger/reversals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(transferId, "API-REV-001"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.value.originalTransactionId").value(transferId))

        mockMvc.perform(
            post("/api/ledger/reversals")
                .header("x-request-id", "REQ-API-REV-DUP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(transferId, "API-REV-DUP-001"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.contractVersion").value("2026-06-02"))
            .andExpect(jsonPath("$.error.code").value("LEDGER_REVERSAL_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.statusCode").value(409))
            .andExpect(jsonPath("$.error.domain").value("ledger"))
            .andExpect(jsonPath("$.error.invariant").value("one reversal per original transaction"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-API-REV-DUP"))
            .andExpect(jsonPath("$.error.correlationId").value("REQ-API-REV-DUP"))
            .andExpect(jsonPath("$.error.route").value("/api/ledger/reversals"))
            .andExpect(jsonPath("$.error.docs").value("docs/migration/structured-api-error-contract.md"))
            .andExpect(jsonPath("$.error.syntheticOnly").value(true))

        assertEquals(100_000L, ledgerCommandService.balance("ACC-A").availableBalanceMinor)
        assertEquals(0L, ledgerCommandService.balance("ACC-B").availableBalanceMinor)
    }

    private fun reversalJson(originalTransactionId: String, idempotencyKey: String): String =
        """
        {
          "originalTransactionId": "$originalTransactionId",
          "idempotencyKey": "$idempotencyKey",
          "requestedBy": "branch01",
          "requestedChannel": "STAFF_TERMINAL",
          "reason": "Synthetic reversal API parity test"
        }
        """.trimIndent()

    private fun seedAccount(customerId: String, accountId: String, accountNo: String) {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES (:customerId, :customerName, 'STANDARD', 'LOW')
            """.trimIndent(),
            mapOf("customerId" to customerId, "customerName" to "Synthetic $customerId")
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES (:accountId, :customerId, :accountNo, 'KRW', 'ACTIVE')
            """.trimIndent(),
            mapOf("accountId" to accountId, "customerId" to customerId, "accountNo" to accountNo)
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
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
