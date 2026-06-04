package lab.banking.core.customer

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.util.Base64
import lab.banking.core.testsupport.ParameterSeedSupport
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
class CustomerTransferApiParityIntegrationTest {
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
              customer_transfer_results,
              fds_case_timeline,
              fds_cases,
              outbox_events,
              idempotency_keys,
              ledger_postings,
              ledger_transactions,
              account_balance_projections,
              account_holds,
              account_limits,
              audit_events,
              accounts,
              customers
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        ParameterSeedSupport.reseedFdsRuleParameters(jdbc)
        seedCustomersAndAccounts()
    }

    @Test
    fun `customer transfer retries replay one posted ledger result and failures are structured`() {
        val commandJson = """
            {
              "customerId": "SYN-CUS-001",
              "fromAccountId": "ACC-CWB-FROM",
              "toAccountId": "ACC-CWB-TO",
              "amountMinor": 1000,
              "idempotencyKey": "CWB-TRANSFER-RETRY-001",
              "requestedBy": "SYN-CUS-001",
              "reason": "Synthetic customer transfer retry parity"
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
            .andExpect(jsonPath("$.item.idempotencyKey").value("CWB-TRANSFER-RETRY-001"))
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

        assertEquals(1, countRows("ledger_transactions WHERE idempotency_key = 'CWB-TRANSFER-RETRY-001'"))
        assertEquals(99_000L, balance("ACC-CWB-FROM"))
        assertEquals(1_000L, balance("ACC-CWB-TO"))

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-TRANSFER-INSUFFICIENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-CWB-FROM",
                      "toAccountId": "ACC-CWB-TO",
                      "amountMinor": 200000,
                      "idempotencyKey": "CWB-TRANSFER-FAIL-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic customer transfer insufficient balance parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("LEDGER_INSUFFICIENT_AVAILABLE_BALANCE"))
            .andExpect(jsonPath("$.error.domain").value("ledger"))
            .andExpect(jsonPath("$.error.invariant").value("available_balance >= transfer amount"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-TRANSFER-INSUFFICIENT"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/transfers"))

        assertEquals(0, countRows("ledger_transactions WHERE idempotency_key = 'CWB-TRANSFER-FAIL-001'"))
        assertEquals(99_000L, balance("ACC-CWB-FROM"))
        assertEquals(1_000L, balance("ACC-CWB-TO"))
    }

    @Test
    fun `customer transfer rejects account ownership mismatch before ledger posting`() {
        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .header("x-request-id", "REQ-CWB-TRANSFER-OWNERSHIP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-CWB-OTHER",
                      "toAccountId": "ACC-CWB-TO",
                      "amountMinor": 1000,
                      "idempotencyKey": "CWB-TRANSFER-OWNERSHIP-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic ownership failure parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_POLICY_VIOLATION"))
            .andExpect(jsonPath("$.error.domain").value("auth"))
            .andExpect(jsonPath("$.error.policy").value("RBAC_ABAC_POLICY_REQUIRED"))
            .andExpect(jsonPath("$.error.requestId").value("REQ-CWB-TRANSFER-OWNERSHIP"))
            .andExpect(jsonPath("$.error.route").value("/api/customer/transfers"))

        assertEquals(0, countRows("ledger_transactions WHERE idempotency_key = 'CWB-TRANSFER-OWNERSHIP-001'"))
    }

    @Test
    fun `customer transaction history shares ledger source with staff history and exposes held FDS statuses`() {
        seedHeldFdsCase()

        val transfer = mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-CWB-FROM",
                      "toAccountId": "ACC-CWB-TO",
                      "amountMinor": 12345,
                      "idempotencyKey": "CWB-TRANSFER-HISTORY-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic customer history parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.item.status").value("POSTED"))
            .andReturn()

        val transactionId = objectMapper.readTree(transfer.response.contentAsString)
            .path("item")
            .path("transactionId")
            .asText()

        mockMvc.perform(
            get("/api/customer/transactions")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
                .queryParam("accountId", "ACC-CWB-FROM")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].transactionId").value(transactionId))
            .andExpect(jsonPath("$.items[0].transactionType").value("INTERNAL_TRANSFER"))
            .andExpect(jsonPath("$.items[0].requestedChannel").value("CUSTOMER_WEB"))
            .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
            .andExpect(jsonPath("$.items[0].amountMinor").value(12345))

        mockMvc.perform(
            get("/api/staff/transactions/search")
                .header("Authorization", bearer("branch01", listOf("BRANCH_STAFF")))
                .queryParam("accountId", "ACC-CWB-FROM")
                .queryParam("reason", "Compare customer source")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].ledgerTransactionId").value(transactionId))
            .andExpect(jsonPath("$.items[0].transactionType").value("INTERNAL_TRANSFER"))
            .andExpect(jsonPath("$.items[0].requestedChannel").value("CUSTOMER_WEB"))
            .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))

        val statuses = mockMvc.perform(
            get("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andReturn()

        val statusItems = objectMapper.readTree(statuses.response.contentAsString).path("items")
        val heldStatus = statusItems.first { it.path("caseId").asText() == "FDS-CWB-HELD-001" }
        assertEquals("HELD", heldStatus.path("status").asText())
        assertEquals("INVESTIGATING", heldStatus.path("caseStatus").asText())
        assertEquals("HELD", heldStatus.path("transferStatus").asText())
        assertEquals(15_000_000L, heldStatus.path("amountMinor").asLong())

        assertEquals(0, countRows("ledger_transactions WHERE idempotency_key = 'IDEMP-FDS-CWB-HELD-001'"))
    }

    @Test
    fun `customer transfer holds and failed attempts are durable statuses without ledger postings`() {
        val beforeCount = countRows("ledger_transactions")

        val held = mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-CWB-FROM",
                      "toAccountId": "ACC-CWB-TO",
                      "amountMinor": 5000000,
                      "idempotencyKey": "CWB-TRANSFER-HELD-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic customer held transfer parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("HELD"))
            .andExpect(jsonPath("$.item.caseId").exists())
            .andReturn()

        val heldCaseId = objectMapper.readTree(held.response.contentAsString)
            .path("item")
            .path("caseId")
            .asText()

        mockMvc.perform(
            post("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "SYN-CUS-001",
                      "fromAccountId": "ACC-CWB-FROM",
                      "toAccountId": "ACC-CWB-TO",
                      "amountMinor": -1,
                      "idempotencyKey": "CWB-TRANSFER-FAILED-001",
                      "requestedBy": "SYN-CUS-001",
                      "reason": "Synthetic customer failed transfer parity"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.item.status").value("FAILED"))
            .andExpect(jsonPath("$.item.failureCode").value("REQUEST_VALIDATION_FAILED"))

        val results = mockMvc.perform(
            get("/api/customer/transfers")
                .header("Authorization", bearer("customer01", listOf("CUSTOMER"), customerId = "SYN-CUS-001"))
                .queryParam("customerId", "SYN-CUS-001")
        )
            .andExpect(status().isOk)
            .andReturn()

        val items = objectMapper.readTree(results.response.contentAsString).path("items")
        val heldStatus = items.first { it.path("caseId").asText() == heldCaseId }
        val failedStatus = items.first { it.path("idempotencyKey").asText() == "CWB-TRANSFER-FAILED-001" }
        assertEquals("HELD", heldStatus.path("status").asText())
        assertEquals("HELD", heldStatus.path("transferStatus").asText())
        assertEquals("FAILED", failedStatus.path("status").asText())
        assertEquals("REQUEST_VALIDATION_FAILED", failedStatus.path("failureCode").asText())
        assertEquals(beforeCount, countRows("ledger_transactions"))
        assertEquals(0, countRows("ledger_transactions WHERE idempotency_key IN ('CWB-TRANSFER-HELD-001', 'CWB-TRANSFER-FAILED-001')"))
    }

    private fun seedCustomersAndAccounts() {
        jdbc.update(
            """
            INSERT INTO customers (customer_id, customer_name, customer_grade, risk_grade)
            VALUES
              ('SYN-CUS-001', 'Lab Customer Alpha', 'STANDARD', 'LOW'),
              ('SYN-CUS-002', 'Lab Customer Beta', 'STANDARD', 'LOW')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO accounts (account_id, customer_id, account_no, currency, status)
            VALUES
              ('ACC-CWB-FROM', 'SYN-CUS-001', 'LAB-001-000101', 'KRW', 'ACTIVE'),
              ('ACC-CWB-TO', 'SYN-CUS-002', 'LAB-002-000102', 'KRW', 'ACTIVE'),
              ('ACC-CWB-OTHER', 'SYN-CUS-002', 'LAB-002-000103', 'KRW', 'ACTIVE')
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
        jdbc.update(
            """
            INSERT INTO account_balance_projections (
              account_id, currency, ledger_balance_minor, available_balance_minor, hold_amount_minor
            )
            VALUES
              ('ACC-CWB-FROM', 'KRW', 100000, 100000, 0),
              ('ACC-CWB-TO', 'KRW', 0, 0, 0),
              ('ACC-CWB-OTHER', 'KRW', 50000, 50000, 0)
            """.trimIndent(),
            emptyMap<String, Any?>()
        )
    }

    private fun seedHeldFdsCase() {
        jdbc.update(
            """
            INSERT INTO fds_cases (
              fds_case_id, transfer_reference_id, customer_id, status, risk_score,
              alerts_json, owner_id, from_account_id, to_account_id, amount_minor,
              transfer_idempotency_key, requested_by, business_date, transfer_status
            )
            VALUES (
              'FDS-CWB-HELD-001', 'TR-CWB-HELD-001', 'SYN-CUS-001', 'INVESTIGATING', 820,
              '[{"ruleId":"FDS-RULE-HIGH-AMOUNT","message":"Synthetic held customer transfer"}]'::jsonb,
              'fds01', 'ACC-CWB-FROM', 'ACC-CWB-TO', 15000000,
              'IDEMP-FDS-CWB-HELD-001', 'customer01', CURRENT_DATE, 'HELD'
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
