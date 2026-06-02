package lab.banking.core.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Paths
import java.time.LocalDate
import lab.banking.core.ledger.application.LedgerCommandService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("api-error-parity")
class StructuredApiErrorContractIntegrationTest {
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
    fun `real ledger endpoints return structured ledger invariant errors`() {
        seedAccount("CUS-ERR-A", "ACC-ERR-A", "LAB-ERR-000001")
        seedAccount("CUS-ERR-B", "ACC-ERR-B", "LAB-ERR-000002")

        assertStructuredError(
            error = postJson(
                route = "/api/ledger/withdrawals",
                requestId = "REQ-SPRING-INSUFFICIENT",
                body = """
                    {
                      "accountId": "ACC-ERR-A",
                      "amountMinor": 1000,
                      "idempotencyKey": "SPRING-ERR-WDR-001",
                      "requestedBy": "branch01",
                      "requestedChannel": "STAFF_TERMINAL",
                      "reason": "Synthetic insufficient balance parity test"
                    }
                """.trimIndent(),
                expectedStatus = 409
            ),
            expected = ExpectedError(
                code = "LEDGER_INSUFFICIENT_AVAILABLE_BALANCE",
                statusCode = 409,
                domain = "ledger",
                invariant = "available_balance >= withdrawal amount",
                requestId = "REQ-SPRING-INSUFFICIENT",
                route = "/api/ledger/withdrawals"
            )
        )

        val closedDate = LocalDate.of(2026, 1, 31)
        postJson(
            route = "/api/ops/daily-closings",
            requestId = "REQ-SPRING-CLOSE",
            body = """
                {
                  "businessDate": "$closedDate",
                  "idempotencyKey": "SPRING-ERR-CLOSE-001",
                  "requestedBy": "ops01"
                }
            """.trimIndent(),
            expectedStatus = 201
        )
        assertStructuredError(
            error = postJson(
                route = "/api/ledger/deposits",
                requestId = "REQ-SPRING-CLOSED-DAY",
                body = """
                    {
                      "accountId": "ACC-ERR-A",
                      "amountMinor": 1000,
                      "idempotencyKey": "SPRING-ERR-DEP-CLOSED-001",
                      "requestedBy": "branch01",
                      "requestedChannel": "STAFF_TERMINAL",
                      "businessDate": "$closedDate",
                      "reason": "Synthetic closed day parity test"
                    }
                """.trimIndent(),
                expectedStatus = 409
            ),
            expected = ExpectedError(
                code = "LEDGER_CLOSED_DAY_IMMUTABLE",
                statusCode = 409,
                domain = "ledger",
                invariant = "closed business dates reject direct posting",
                requestId = "REQ-SPRING-CLOSED-DAY",
                route = "/api/ledger/deposits"
            )
        )

        postJson(
            route = "/api/ledger/deposits",
            requestId = "REQ-SPRING-SEED-REVERSAL",
            body = """
                {
                  "accountId": "ACC-ERR-A",
                  "amountMinor": 100000,
                  "idempotencyKey": "SPRING-ERR-DEP-REV-001",
                  "requestedBy": "branch01",
                  "requestedChannel": "STAFF_TERMINAL"
                }
            """.trimIndent(),
            expectedStatus = 201
        )
        val transfer = postJson(
            route = "/api/ledger/transfers",
            requestId = "REQ-SPRING-TRANSFER",
            body = """
                {
                  "fromAccountId": "ACC-ERR-A",
                  "toAccountId": "ACC-ERR-B",
                  "amountMinor": 12000,
                  "idempotencyKey": "SPRING-ERR-TRF-001",
                  "requestedBy": "customer01",
                  "requestedChannel": "CUSTOMER_WEB"
                }
            """.trimIndent(),
            expectedStatus = 201
        )
        val transferId = transfer.path("value").path("id").asText()
        postJson(
            route = "/api/ledger/reversals",
            requestId = "REQ-SPRING-REVERSAL",
            body = reversalJson(transferId, "SPRING-ERR-REV-001"),
            expectedStatus = 201
        )
        assertStructuredError(
            error = postJson(
                route = "/api/ledger/reversals",
                requestId = "REQ-SPRING-REVERSAL-DUP",
                body = reversalJson(transferId, "SPRING-ERR-REV-DUP-001"),
                expectedStatus = 409
            ),
            expected = ExpectedError(
                code = "LEDGER_REVERSAL_POLICY_VIOLATION",
                statusCode = 409,
                domain = "ledger",
                invariant = "one reversal per original transaction",
                requestId = "REQ-SPRING-REVERSAL-DUP",
                route = "/api/ledger/reversals"
            )
        )

        assertEquals(100_000L, ledgerCommandService.balance("ACC-ERR-A").availableBalanceMinor)
        assertEquals(0L, ledgerCommandService.balance("ACC-ERR-B").availableBalanceMinor)
    }

    @Test
    fun `migration parity probe proves non-ledger structured error families through Spring handler`() {
        listOf(
            ExpectedError(
                code = "POLICY_REASON_REQUIRED",
                statusCode = 400,
                domain = "audit",
                policy = "REASON_REQUIRED",
                requestId = "REQ-SPRING-POLICY-REASON",
                route = "/api/parity/structured-errors/POLICY_REASON_REQUIRED"
            ),
            ExpectedError(
                code = "AUTHORIZATION_POLICY_VIOLATION",
                statusCode = 403,
                domain = "auth",
                policy = "RBAC_ABAC_REQUIRED",
                requestId = "REQ-SPRING-AUTHZ",
                route = "/api/parity/structured-errors/AUTHORIZATION_POLICY_VIOLATION"
            ),
            ExpectedError(
                code = "MAKER_CHECKER_SELF_APPROVAL_REJECTED",
                statusCode = 409,
                domain = "maker-checker",
                policy = "MAKER_CHECKER_SEPARATION_OF_DUTIES",
                requestId = "REQ-SPRING-MAKER-CHECKER",
                route = "/api/parity/structured-errors/MAKER_CHECKER_SELF_APPROVAL_REJECTED"
            ),
            ExpectedError(
                code = "REQUEST_VALIDATION_FAILED",
                statusCode = 400,
                domain = "validation",
                requestId = "REQ-SPRING-VALIDATION",
                route = "/api/parity/structured-errors/REQUEST_VALIDATION_FAILED"
            ),
            ExpectedError(
                code = "RESOURCE_NOT_FOUND",
                statusCode = 404,
                domain = "resource",
                requestId = "REQ-SPRING-NOT-FOUND",
                route = "/api/parity/structured-errors/RESOURCE_NOT_FOUND"
            ),
            ExpectedError(
                code = "WORKFLOW_STATE_VIOLATION",
                statusCode = 409,
                domain = "workflow",
                policy = "VALID_WORKFLOW_TRANSITION_REQUIRED",
                requestId = "REQ-SPRING-WORKFLOW",
                route = "/api/parity/structured-errors/WORKFLOW_STATE_VIOLATION"
            ),
            ExpectedError(
                code = "INTERNAL_RUNTIME_ERROR",
                statusCode = 500,
                domain = "runtime",
                requestId = "REQ-SPRING-INTERNAL",
                route = "/api/parity/structured-errors/INTERNAL_RUNTIME_ERROR"
            )
        ).forEach { expected ->
            assertStructuredError(
                error = getError(expected.route, expected.requestId, expected.statusCode),
                expected = expected
            )
        }
    }

    private fun getError(route: String, requestId: String, expectedStatus: Int): JsonNode {
        val response = mockMvc.perform(
            get(route)
                .header("x-request-id", requestId)
        )
            .andExpect(status().`is`(expectedStatus))
            .andReturn()
            .response
        return objectMapper.readTree(response.contentAsString).path("error")
    }

    private fun postJson(route: String, requestId: String, body: String, expectedStatus: Int? = null): JsonNode {
        val result = mockMvc.perform(
            post(route)
                .header("x-request-id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        if (expectedStatus != null) {
            result.andExpect(status().`is`(expectedStatus))
        }
        return objectMapper.readTree(result.andReturn().response.contentAsString)
    }

    private fun assertStructuredError(error: JsonNode, expected: ExpectedError) {
        val node = if (error.has("error")) error.path("error") else error
        assertEquals("2026-06-02", node.path("contractVersion").asText())
        assertEquals(expected.code, node.path("code").asText())
        assertTrue(node.path("message").asText().isNotBlank())
        assertEquals(expected.statusCode, node.path("statusCode").asInt())
        assertEquals(expected.domain, node.path("domain").asText())
        assertNullableText(expected.invariant, node, "invariant")
        assertNullableText(expected.policy, node, "policy")
        assertTrue(node.path("cause").asText().isNotBlank())
        assertTrue(node.path("fix").asText().isNotBlank())
        assertEquals(expected.requestId, node.path("requestId").asText())
        assertEquals(expected.requestId, node.path("correlationId").asText())
        assertEquals(expected.route, node.path("route").asText())
        assertEquals("docs/migration/structured-api-error-contract.md", node.path("docs").asText())
        assertEquals(true, node.path("syntheticOnly").asBoolean())
    }

    private fun assertNullableText(expected: String?, error: JsonNode, field: String) {
        assertTrue(error.has(field), "structured error must include $field")
        if (expected == null) {
            assertTrue(error.get(field).isNull, "$field should be null")
        } else {
            assertEquals(expected, error.path(field).asText())
        }
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

    private data class ExpectedError(
        val code: String,
        val statusCode: Int,
        val domain: String,
        val invariant: String? = null,
        val policy: String? = null,
        val requestId: String,
        val route: String
    )

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
