package lab.banking.core.ledger.api

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["banking-lab.security.enabled=true"])
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
              customer_transfer_results,
              fds_case_timeline,
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
    fun `runtime health exposes valid audit hash chain`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditHashChainValid").value(true))
            .andExpect(jsonPath("$.nodeReferenceRuntimeRetained").value(true))
            .andExpect(jsonPath("$.migrationTarget").value("kotlin-spring-boot"))
    }

    @Test
    fun `staff customer search enforces reason and records audit event`() {
        seedRuntimeCustomersAndAccounts()

        mockMvc.perform(
            get("/api/staff/customers/search")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("POLICY_REASON_REQUIRED"))

        assertEquals(0, countRows("audit_events WHERE event_type = 'CUSTOMER_SEARCH'"))

        mockMvc.perform(
            get("/api/staff/customers/search")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .queryParam("query", "alpha")
                .queryParam("reason", "Customer requested support")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.auditEventId").exists())

        assertEquals(1, countRows("audit_events WHERE event_type = 'CUSTOMER_SEARCH'"))
    }

    @Test
    fun `customer transfer endpoint is idempotent`() {
        seedRuntimeCustomersAndAccounts()

        val commandJson = """
            {
              "customerId": "SYN-CUS-001",
              "fromAccountId": "ACC-RUNTIME-FROM",
              "toAccountId": "ACC-RUNTIME-TO",
              "amountMinor": 1200,
              "idempotencyKey": "RUNTIME-IDEMP-001",
              "requestedBy": "SYN-CUS-001",
              "reason": "Runtime customer transfer idempotency parity"
            }
        """.trimIndent()

        val first = mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(commandJson)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("POSTED"))
            .andReturn()

        val transactionId = objectMapper.readTree(first.response.contentAsString)
            .path("item")
            .path("transactionId")
            .asText()

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(commandJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(true))
            .andExpect(jsonPath("$.item.transactionId").value(transactionId))
            .andExpect(jsonPath("$.item.status").value("POSTED"))

        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'RUNTIME-IDEMP-001'"))
        assertEquals(1, countRows("customer_transfer_results WHERE idempotency_key = 'RUNTIME-IDEMP-001'"))
        assertEquals(98_800L, balance("ACC-RUNTIME-FROM"))
        assertEquals(1_200L, balance("ACC-RUNTIME-TO"))
    }

    @Test
    fun `ledger withdrawal and reversal endpoints preserve invariants`() {
        seedAccount("CUS-WDR", "ACC-WDR", "LAB-API-000003", availableBalanceMinor = 50_000)

        val withdrawal = mockMvc.perform(
            post("/api/ledger/withdrawals")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountId": "ACC-WDR",
                      "amountMinor": 3000,
                      "idempotencyKey": "RUNTIME-WDR-001",
                      "requestedBy": "branch01",
                      "requestedChannel": "STAFF_TERMINAL",
                      "reason": "Runtime ledger withdrawal test"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.value.transactionType").value("WITHDRAWAL"))
            .andReturn()

        val withdrawalId = objectMapper.readTree(withdrawal.response.contentAsString)
            .path("value")
            .path("id")
            .asText()

        mockMvc.perform(
            post("/api/ledger/reversals")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(withdrawalId, "RUNTIME-REV-001"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.value.originalTransactionId").value(withdrawalId))
            .andExpect(jsonPath("$.value.transactionType").value("REVERSAL"))

        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'RUNTIME-WDR-001'"))
        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'RUNTIME-REV-001'"))
        assertEquals(50_000L, balance("ACC-WDR"))
    }

    @Test
    fun `ledger reversal endpoint preserves invariants and returns structured duplicate reversal policy error`() {
        seedAccount("CUS-A", "ACC-A", "LAB-API-000001")
        seedAccount("CUS-B", "ACC-B", "LAB-API-000002")

        mockMvc.perform(
            post("/api/ledger/deposits")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
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
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
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
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reversalJson(transferId, "API-REV-001"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.value.originalTransactionId").value(transferId))

        mockMvc.perform(
            post("/api/ledger/reversals")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
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

    private fun seedRuntimeCustomersAndAccounts() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('SYN-CUS-001', 'Alpha Customer One', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Alpha Customer Two', 'STANDARD', 'LOW'),
              ('SYN-CUS-003', 'Alpha Customer Three', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-RUNTIME-FROM', 'SYN-CUS-001', 'LAB-RT-000101', 'KRW', 'ACTIVE'),
              ('ACC-RUNTIME-TO', 'SYN-CUS-002', 'LAB-RT-000102', 'KRW', 'ACTIVE'),
              ('ACC-RUNTIME-OTHER', 'SYN-CUS-003', 'LAB-RT-000103', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('ACC-RUNTIME-FROM', 'KRW', 100000, 100000, 0),
              ('ACC-RUNTIME-TO', 'KRW', 0, 0, 0),
              ('ACC-RUNTIME-OTHER', 'KRW', 50000, 50000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedAccount(
        customerId: String,
        accountId: String,
        accountNo: String,
        availableBalanceMinor: Long = 0L
    ) {
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
        if (availableBalanceMinor != 0L) {
            jdbc.update(
                """
                INSERT INTO account_balance_projections (
                  account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
                )
                VALUES (:accountId, 'KRW', :availableBalanceMinor, :availableBalanceMinor, 0)
                """.trimIndent(),
                mapOf("accountId" to accountId, "availableBalanceMinor" to availableBalanceMinor)
            )
        }
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
        jdbc.queryForObject(
            "SELECT count(*) FROM $tableExpression",
            emptyMap<String, Any?>(),
            Int::class.java
        ) ?: 0

    private fun balance(accountId: String): Long =
        jdbc.queryForObject(
            "SELECT available_balance_minor FROM account_balance_projections WHERE account_id = :accountId",
            mapOf("accountId" to accountId),
            Long::class.java
        ) ?: 0L

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
